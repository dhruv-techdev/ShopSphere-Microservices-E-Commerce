# syntax=docker/dockerfile:1
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Restore first for layer caching
COPY src/ShippingService.Api/ShippingService.Api.csproj src/ShippingService.Api/
RUN dotnet restore src/ShippingService.Api/ShippingService.Api.csproj

COPY src/ src/
RUN dotnet publish src/ShippingService.Api/ShippingService.Api.csproj \
    -c Release -o /app/publish --no-restore /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /app/publish .
EXPOSE 8088
USER app
ENTRYPOINT ["dotnet", "ShippingService.Api.dll"]
