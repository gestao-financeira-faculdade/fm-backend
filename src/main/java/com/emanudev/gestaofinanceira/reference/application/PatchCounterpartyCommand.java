package com.emanudev.gestaofinanceira.reference.application;

public record PatchCounterpartyCommand(String legalName, String tradeName, Boolean active) {
}
