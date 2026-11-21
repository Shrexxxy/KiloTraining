---
name: mvi-architecture
description: Применяй для моделирования состояния экрана, пользовательских событий и асинхронной логики в Android ViewModel.
---

# mvi-architecture

Используй шаблон ниже как основу. Подставь имена экрана и событий из задачи. Все вложенные значения состояния также должны быть неизменяемыми. Публикуй новые снимки коллекций при изменениях.

```kotlin
data class UiState(val portionInput: String = "")

sealed interface UiEvent {
    data class PortionChanged(val text: String) : UiEvent
}

class FoodViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = mutableState.asStateFlow()

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.PortionChanged -> mutableState.update { it.copy(portionInput = event.text) }
        }
    }
}
```

Запускай операции экрана в `viewModelScope`. Блокирующий ввод-вывод переключай на подходящий диспетчер в слое данных. Для сменяющих друг друга поисковых запросов отменяй устаревшую работу или проверяй идентификатор запроса до публикации результата.

Преобразуй ожидаемые исходы domain-слоя в типизированное состояние экрана. Например, для штрихкода различай отсутствие карточки и недоступность сети, чтобы UI мог предложить ручной ввод или повтор. При перехвате исключений пропускай `CancellationException` дальше. В тестах проверяй переходы состояния по событиям и результатам подставного репозитория.
