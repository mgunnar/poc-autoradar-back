package com.autoradar.business;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.Normalizer;

@Service
public class CarDataParser {

    /**
     * Transforma strings de preço (ex: "R$ 85.000,00", "USD 2000") em Double puro.
     * Retorna null se não conseguir converter, permitindo tratamento posterior.
     */
    public Double parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) return null;
        try {
            // Remove tudo que não é número ou vírgula
            String clean = rawPrice.replaceAll("[^0-9,]", "");
            if (clean.isBlank()) return null;
            // Padroniza decimal para ponto
            clean = clean.replace(",", ".");
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Limpa títulos, remove espaços extras e capitaliza corretamente.
     */
    public String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) return "Título Indisponível";
        // Normaliza caracteres (remove acentos estranhos se necessário) e espaços duplos
        return rawTitle.trim().replaceAll("\\s+", " ");
    }
}