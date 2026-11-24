package app.calorietraining

import android.app.Application
import android.database.SQLException
import android.net.Uri
import java.io.File
import java.io.IOException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface CatalogSearch {
    data object Idle : CatalogSearch
    data class Loading(val term: String, val serverFoods: List<FoodDraft>? = null) : CatalogSearch
    data class Results(val term: String, val foods: List<FoodDraft>,
        val serverFoods: List<FoodDraft>? = null) : CatalogSearch
    data class Failed(val term: String, val serverFoods: List<FoodDraft>? = null) : CatalogSearch
}

data class FoodUiState(
    val day: LocalDate = LocalDate.now(),
    val rows: List<FoodEntry> = emptyList(),
    val recent: List<FoodDraft> = emptyList(),
    val library: List<FoodDraft>? = null,
    val addingMeal: Meal? = null,
    val frequent: List<FoodDraft> = emptyList(),
    val myProducts: List<MyProduct> = emptyList(),
    val managingProducts: Boolean = false,
    val online: CatalogSearch = CatalogSearch.Idle,
    val goals: DailyGoals = DailyGoals(),
    val draft: FoodDraft? = null,
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val scanning: Boolean = false,
    val hint: String = "",
    val scanOutcome: ScanOutcome? = null,
    val recipes: List<Recipe>? = null,
    val recipe: Recipe? = null,
    val ingredientIndex: Int? = null,
    val confirmingDelete: Boolean = false,
    val venues: List<Venue> = emptyList(),
    val venueTab: Boolean = false,
    val openVenue: Venue? = null,
    val venueDishes: List<FoodDraft> = emptyList()
)

sealed interface FoodEvent {
    data class Day(val value: LocalDate) : FoodEvent
    data class Edit(val value: FoodDraft) : FoodEvent
    data class FindFood(val term: String) : FoodEvent
    data class AddInMeal(val meal: Meal) : FoodEvent
    data class AddNextMeal(val day: LocalDate) : FoodEvent
    data object Venues : FoodEvent
    data object LeaveVenues : FoodEvent
    data class OpenVenue(val venue: Venue) : FoodEvent
    data object BackFromVenue : FoodEvent
    data class VenuePhoto(val uri: Uri, val capture: File? = null, val venueId: Long? = null) : FoodEvent
    data object VenuePhotoFailed : FoodEvent
    data object Recipes : FoodEvent
    data object LeaveRecipes : FoodEvent
    data class EditRecipe(val value: Recipe?) : FoodEvent
    data class LogRecipe(val value: Recipe) : FoodEvent
    data class Ingredient(val index: Int?) : FoodEvent
    data object SaveRecipe : FoodEvent
    data object BrowseFoods : FoodEvent
    data object ManageProducts : FoodEvent
    data object LeaveProducts : FoodEvent
    data class SaveProduct(val product: MyProduct) : FoodEvent
    data class DeleteProduct(val id: Long) : FoodEvent
    data object BackToEditor : FoodEvent
    data object Scan : FoodEvent
    data class Barcode(val code: String) : FoodEvent
    data class Goal(val kcal: Int?, val macros: MacroGoals) : FoodEvent
    data class CopyMeal(val source: LocalDate, val destination: LocalDate, val meal: Meal) : FoodEvent
    data object Close : FoodEvent
    data object Save : FoodEvent
    data object AskDelete : FoodEvent
    data object CancelDelete : FoodEvent
    data object Delete : FoodEvent
}

class FoodViewModel internal constructor(
    application: Application,
    private val io: CoroutineDispatcher,
    private val searchServer: (ProductServerSettings) -> ProductServer?,
    private val catalogSearch: suspend (String) -> List<FoodDraft>
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, Dispatchers.IO,
        ProductServerSettings::client, ::searchCatalog)

    private val state = MutableStateFlow(FoodUiState())
    val uiState = state.asStateFlow()

    private var unsavedPhoto: String? = null

    private val productServer = ProductServerSettings(application)

    init {
        refresh(state.value.day)
        ProductServerSettings.queueChanged()
        syncProducts()
    }

    private fun syncProducts() {
        viewModelScope.launch { productServer.send() }
    }

    fun onEvent(event: FoodEvent) {
        val current = state.value
        if (current.busy) {
            if (event is FoodEvent.VenuePhoto) event.capture?.delete()
            return
        }
        when (event) {
            is FoodEvent.AddInMeal -> openMeal(event.meal)
            is FoodEvent.AddNextMeal -> openMeal(null, event.day)
            FoodEvent.Venues -> state.value = current.copy(venueTab = true, recipes = null, error = null)
            FoodEvent.LeaveVenues -> state.value = current.copy(venueTab = false, openVenue = null, error = null)
            is FoodEvent.OpenVenue -> openVenue(current, event.venue)
            FoodEvent.BackFromVenue -> state.value = current.copy(openVenue = null, venueDishes = emptyList(), error = null)
            is FoodEvent.VenuePhoto -> importVenuePhoto(current, event)
            FoodEvent.VenuePhotoFailed -> state.value = current.copy(error = "Не удалось открыть камеру или галерею")
            FoodEvent.Recipes -> refresh(current.day, showRecipes = true)
            FoodEvent.LeaveRecipes -> state.value = current.copy(recipes = null, recipe = null, error = null)
            is FoodEvent.EditRecipe -> state.value = current.copy(recipe = event.value, error = null)
            is FoodEvent.LogRecipe -> event.value.food()?.let {
                state.value = current.copy(recipes = null, recipe = null, draft = it.copy(meal = current.addingMeal ?: Meal.UNSPECIFIED, mealTime = defaultMealTime(current.day)))
            }
            is FoodEvent.Ingredient -> {
                val item = event.index?.let { current.recipe?.ingredients?.getOrNull(it) } ?: FoodDraft(weighted = true)
                state.value = current.copy(draft = item, scanOutcome = null, ingredientIndex = event.index, error = null)
            }
            FoodEvent.SaveRecipe -> current.recipe?.takeIf { it.food() != null }?.let {
                refresh(current.day, showRecipes = true) { saveRecipe(it) }
            }
            is FoodEvent.Day -> refresh(event.value)
            is FoodEvent.Goal -> refresh(current.day) {
                setGoals(current.day, DailyGoals(event.kcal, event.macros))
            }
            is FoodEvent.CopyMeal -> if (current.day == event.source && current.rows.any { it.meal == event.meal }) {
                refresh(event.destination) { copyMeal(event.source, event.destination, event.meal) }
            }
            is FoodEvent.Edit -> {
                if (current.draft?.photoPath != event.value.photoPath) discardUnsavedPhoto()
                val draft = if (current.draft == null && event.value.id == null) {
                    event.value.copy(mealTime = defaultMealTime(current.day))
                } else event.value
                state.value = current.copy(draft = draft, error = null, library = null)
            }
            is FoodEvent.FindFood -> findFood(event.term.trim())
            FoodEvent.BackToEditor -> state.value = current.copy(library = null)
            FoodEvent.ManageProducts -> state.value = current.copy(managingProducts = true)
            FoodEvent.LeaveProducts -> state.value = current.copy(managingProducts = false, error = null)
            is FoodEvent.SaveProduct -> changeProducts(sync = true) { saveMyProduct(event.product) }
            is FoodEvent.DeleteProduct -> changeProducts { deleteMyProduct(event.id) }
            FoodEvent.BrowseFoods -> {
                state.value = current.copy(busy = true, error = null)
                viewModelScope.launch {
                    try {
                        val foods = withContext(io) { FoodStore(getApplication()).use { it.myProducts() } }
                        state.value = current.copy(busy = false, library = foods.map { it.toDraft() }, myProducts = foods, draft = current.draft ?: FoodDraft(mealTime = defaultMealTime(current.day)), online = CatalogSearch.Idle, error = null)
                    } catch (e: SQLException) {
                        state.value = current.copy(busy = false, error = "Не удалось загрузить список продуктов")
                    }
                }
            }
            FoodEvent.Scan -> state.value = current.copy(scanning = true, error = null)
            is FoodEvent.Barcode -> lookup(event.code)
            FoodEvent.Close -> {
                val keepDraft = current.scanning && current.recipe != null
                val newEntry = current.draft != null && current.draft.id == null
                val keepMeal = current.scanning || current.recipe != null || (current.addingMeal != null && newEntry)
                if (!keepDraft) discardUnsavedPhoto()
                if (!keepMeal) searchJob?.cancel()
                state.value = current.copy(draft = current.draft.takeIf { keepDraft }, scanning = false,
                    addingMeal = current.addingMeal.takeIf { keepMeal },
                    venueTab = keepMeal && current.venueTab,
                    openVenue = current.openVenue.takeIf { keepMeal },
                    recipes = current.recipes.takeUnless { current.addingMeal != null && !keepMeal }, hint = "",
                    scanOutcome = current.scanOutcome.takeIf { keepDraft }, confirmingDelete = false, error = null)
            }
            FoodEvent.Save -> current.draft?.takeIf { it.calories != null }?.let { draft ->
                val recipe = current.recipe
                if (recipe == null) refresh(current.day, sync = !draft.fromRecipe) { save(current.day, draft) }
                else {
                    val ingredients = recipe.ingredients.toMutableList()
                    val index = current.ingredientIndex
                    if (index == null) ingredients.add(draft) else ingredients[index] = draft
                    state.value = current.copy(recipe = recipe.copy(ingredients = ingredients), draft = null, scanOutcome = null, ingredientIndex = null)
                }
            }
            FoodEvent.AskDelete -> state.value = current.copy(confirmingDelete = true)
            FoodEvent.CancelDelete -> state.value = current.copy(confirmingDelete = false)
            FoodEvent.Delete -> current.draft?.id?.let { id ->
                refresh(current.day) { delete(current.day, id) }
            }
        }
    }

    /** Открывает добавление в приём пищи; без приёма выбирает первый незаполненный в указанном дне. */
    private fun openMeal(meal: Meal?, day: LocalDate = state.value.day) {
        val current = state.value
        state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(io) {
                    FoodStore(getApplication()).use { store ->
                        val base = if (day == current.day && meal != null) current else current.copy(day = day,
                            rows = store.entries(day), goals = store.goalsFor(day), loaded = true,
                            draft = null, recipes = null, recipe = null)
                        base.copy(addingMeal = meal ?: nextMealToFill(base.rows.map { it.meal }),
                            frequent = store.frequentFoods(day), recent = store.foodSuggestions(day, 12),
                            myProducts = store.myProducts(), venues = store.venues(),
                            venueTab = false, openVenue = null, online = CatalogSearch.Idle, error = null)
                    }
                }
                state.value = snapshot
            } catch (e: SQLException) {
                state.value = state.value.copy(busy = false, error = "Не удалось загрузить продукты")
            }
        }
    }

    private fun openVenue(current: FoodUiState, venue: Venue) {
        state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val dishes = withContext(io) { FoodStore(getApplication()).use { it.venueDishes(venue) } }
                state.value = current.copy(openVenue = venue, venueDishes = dishes)
            } catch (e: SQLException) {
                state.value = current.copy(error = "Не удалось загрузить блюда")
            }
        }
    }

    private fun importVenuePhoto(current: FoodUiState, event: FoodEvent.VenuePhoto) {
        val draft = current.draft
        val venue = current.openVenue
        if (event.venueId == null && draft == null || event.venueId != null && venue?.id != event.venueId) {
            event.capture?.delete()
            return
        }
        state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val path = withContext(io) {
                    try {
                        getApplication<Application>().saveVenuePhoto(event.uri, event.capture).also { photo ->
                            event.venueId?.let { id ->
                                try {
                                    FoodStore(getApplication()).use { it.setVenueLogo(id, photo) }
                                } catch (e: SQLException) {
                                    File(photo).delete()
                                    throw e
                                }
                            }
                        }
                    } finally {
                        event.capture?.delete()
                    }
                }
                if (event.venueId == null && draft != null) {
                    discardUnsavedPhoto()
                    unsavedPhoto = path
                    state.value = current.copy(draft = draft.copy(photoPath = path, photoHash = null))
                } else if (venue != null) {
                    val updated = venue.copy(logoPath = path)
                    state.value = current.copy(openVenue = updated,
                        venues = current.venues.map { if (it.id == updated.id) updated else it })
                }
            } catch (e: IOException) {
                android.util.Log.w("VenueCamera", "Не удалось обработать фото", e)
                state.value = current.copy(error = "Не удалось прочитать или сохранить фото")
            } catch (e: SecurityException) {
                state.value = current.copy(error = "Фото недоступно")
            } catch (e: SQLException) {
                state.value = current.copy(error = "Не удалось сохранить логотип")
            }
        }
    }

    private fun discardUnsavedPhoto() {
        unsavedPhoto?.let { File(it).delete() }
        unsavedPhoto = null
    }

    override fun onCleared() {
        if (!state.value.busy) discardUnsavedPhoto()
        super.onCleared()
    }

    private fun changeProducts(sync: Boolean = false, change: FoodStore.() -> Unit) {
        val current = state.value
        state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val products = withContext(io) {
                    FoodStore(getApplication()).use { store ->
                        store.change()
                        store.myProducts()
                    }
                }
                state.value = current.copy(busy = false, myProducts = products, library = current.library?.let { products.map(MyProduct::toDraft) })
                ProductServerSettings.queueChanged()
                if (sync) syncProducts()
            } catch (e: SQLException) {
                state.value = current.copy(busy = false, error = "Не удалось изменить продукт")
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun findFood(term: String) {
        if (term.length < 2 || (state.value.library == null && state.value.addingMeal == null)) return
        searchJob?.cancel()
        state.value = state.value.copy(online = CatalogSearch.Loading(term))
        var published = state.value.online
        searchJob = viewModelScope.launch {
            fun publish(result: CatalogSearch) {
                if (state.value.online === published && (state.value.library != null || state.value.addingMeal != null)) {
                    state.value = state.value.copy(online = result)
                    published = state.value.online
                }
            }

            var serverFoods: List<FoodDraft>? = null
            launch {
                serverFoods = withContext(io) {
                    try {
                        searchServer(productServer)?.search(term)?.map { it.toDraft() }
                    } catch (_: java.io.IOException) {
                        null
                    }
                }
                val foods = serverFoods ?: return@launch
                publish(when (val current = published) {
                    is CatalogSearch.Loading -> current.copy(serverFoods = foods)
                    is CatalogSearch.Results -> current.copy(serverFoods = foods)
                    is CatalogSearch.Failed -> current.copy(serverFoods = foods)
                    CatalogSearch.Idle -> return@launch
                })
            }

            val foods = try {
                withContext(io) { catalogSearch(term) }
            } catch (_: java.io.IOException) {
                null
            }
            publish(if (foods == null) CatalogSearch.Failed(term, serverFoods)
                else CatalogSearch.Results(term, foods, serverFoods))
        }
    }

    private fun lookup(code: String) {
        if (!validBarcode(code)) return
        val current = state.value
        val retry = current.draft?.takeIf {
            !current.scanning && current.scanOutcome == ScanOutcome.UNAVAILABLE && it.barcode == code
        }
        state.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val product = withContext(io) { FoodStore(getApplication()).use { it.lookup(code, productServer.client()) } }
                val found = product.draft
                val draft = retry?.copy(
                    name = retry.name.ifBlank { found.name },
                    energy = retry.energy.ifBlank { found.energy },
                    macros = if (retry.macros == Macros()) found.macros else retry.macros,
                    brand = found.brand, origin = found.origin
                ) ?: found.copy(meal = current.draft?.meal ?: current.addingMeal.takeIf { current.recipe == null } ?: Meal.UNSPECIFIED,
                    mealTime = defaultMealTime(current.day))
                state.value = current.copy(draft = draft, hint = product.hint, scanOutcome = product.outcome,
                    busy = false, scanning = false, error = null)
            } catch (e: SQLException) {
                state.value = state.value.copy(error = "Не удалось прочитать каталог", busy = false)
            }
        }
    }

    private fun refresh(day: LocalDate, showRecipes: Boolean = false, sync: Boolean = false, change: (FoodStore.() -> Unit)? = null) {
        val current = state.value
        val pendingPhoto = unsavedPhoto
        state.value = current.copy(busy = true, error = null, confirmingDelete = false)
        viewModelScope.launch {
            try {
                val snapshot = withContext(io) {
                    FoodStore(getApplication()).use { store ->
                        fun snapshot() = FoodUiState(day = day, rows = store.entries(day),
                            goals = store.goalsFor(day), loaded = true, venues = store.venues(),
                            recipes = if (showRecipes) store.recipes() else null,
                            addingMeal = current.addingMeal.takeIf { showRecipes },
                            myProducts = current.myProducts.takeIf { showRecipes }.orEmpty(),
                            frequent = current.frequent.takeIf { showRecipes }.orEmpty(),
                            recent = current.recent.takeIf { showRecipes }.orEmpty())
                        if (change == null) return@use snapshot()
                        val db = store.writableDatabase
                        db.beginTransaction()
                        val updated = try {
                            store.change()
                            snapshot().also { db.setTransactionSuccessful() }
                        } finally { db.endTransaction() }
                        pendingPhoto?.let(store.unusedPhotos::add)
                        store.cleanUnusedPhotos()
                        updated
                    }
                }
                if (change == null) discardUnsavedPhoto() else unsavedPhoto = null
                state.value = snapshot
                if (sync) {
                    ProductServerSettings.queueChanged()
                    syncProducts()
                }
            } catch (e: SQLException) {
                state.value = state.value.copy(busy = false, error = "Не удалось открыть или сохранить дневник. Попробуйте ещё раз.")
            }
        }
    }
}

/** Первый пустой из завтрака, обеда и ужина; если все заполнены — перекус. */
internal fun nextMealToFill(filled: Collection<Meal>): Meal =
    listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER).firstOrNull { it !in filled } ?: Meal.SNACK
