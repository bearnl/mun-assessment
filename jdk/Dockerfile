FROM mcr.microsoft.com/azure-functions/python:4-python3.10-appservice

LABEL org.opencontainers.image.source=https://github.com/bearnl/mun-assessment

RUN apt-get update && apt-get install -y openjdk-17-jdk-headless

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY src/ /home/site/wwwroot/

RUN cd /home/site/wwwroot && pip install -r requirements.txt
