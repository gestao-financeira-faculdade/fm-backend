package com.emanudev.gestaofinanceira.reference.application;

public record PatchCategoryCommand(String name, Boolean requiresAttachmentAlways, Boolean fixedCost, Boolean active) {
}
