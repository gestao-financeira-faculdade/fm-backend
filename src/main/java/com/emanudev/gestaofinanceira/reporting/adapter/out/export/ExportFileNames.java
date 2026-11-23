package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import java.time.LocalDate;

final class ExportFileNames {

    static final String PREFIX = "relatorio-financeiro-";

    private ExportFileNames() {
    }

    static String forToday(String extension) {
        return PREFIX + LocalDate.now() + "." + extension;
    }
}
