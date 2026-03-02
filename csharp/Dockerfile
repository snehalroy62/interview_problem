# syntax=docker/dockerfile:1
FROM mcr.microsoft.com/dotnet/sdk:10.0-noble@sha256:0a506ab0c8aa077361af42f82569d364ab1b8741e967955d883e3f23683d473a AS build
WORKDIR /src

COPY Challenge/Challenge.csproj Challenge/
RUN dotnet restore Challenge/Challenge.csproj

COPY Challenge/ Challenge/
RUN dotnet publish Challenge/Challenge.csproj -o /app/out

FROM mcr.microsoft.com/dotnet/runtime:10.0-noble-chiseled@sha256:4064d8b92a610279f1d6de907c6de9bc4e4a07e4e1f94fdff77cd77a406d9725 AS runtime
WORKDIR /app
COPY --from=build /app/out ./
ENTRYPOINT ["dotnet", "Challenge.dll"]
