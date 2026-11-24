FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY src/CalorieTraining.Server/CalorieTraining.Server.csproj ./
RUN dotnet restore
COPY src/CalorieTraining.Server/ ./
RUN dotnet publish -c Release --no-restore -o /publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /publish .
USER $APP_UID
EXPOSE 5080
ENTRYPOINT ["dotnet", "CalorieTraining.Server.dll"]
