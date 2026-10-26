FROM ubuntu:latest
LABEL authors="emanu"

ENTRYPOINT ["top", "-b"]