package com.autoradar.infrastructure.scraper;

import com.autoradar.business.CarDataParser;
import com.autoradar.domain.dto.CarDTO;
import com.autoradar.domain.scraper.CarScraperStrategy;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SoCarraoScraper implements CarScraperStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SoCarraoScraper.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
    private static final String BASE_URL = "https://www.socarrao.com.br";

    private final CarDataParser parser;

    public SoCarraoScraper(CarDataParser parser) {
        this.parser = parser;
    }

    @Override
    public List<CarDTO> search(String query, String location) {
        List<CarDTO> results = new ArrayList<>();
        
        // --- 1. Definição da URL (Estratégia Híbrida) ---
        String year = extractYear(query);
        String cleanQuery = query.replaceAll("\\b(19|20)\\d{2}\\b", "").trim();
        boolean hasLocation = location != null && !location.isEmpty() && !location.equalsIgnoreCase("brasil");

        String targetUrl;
        if (hasLocation) {
            String searchTerm = (cleanQuery + " " + (year != null ? year : "") + " " + location).trim().replace(" ", "+");
            targetUrl = String.format("%s/buscar?q=%s", BASE_URL, searchTerm);
        } else {
            String modelPath = cleanQuery.toLowerCase().replaceAll("[^a-z0-9 ]", "").replace(" ", "/");
            targetUrl = String.format("%s/%s", BASE_URL, modelPath);
            if (year != null) targetUrl += "/" + year;
        }

        logger.info("SóCarrão Alvo: {}", targetUrl);

        try {
            // --- 2. Conexão ---
            Document doc = fetchDocument(targetUrl);
            
            // Fallback se URL amigável falhar
            if (doc == null && !hasLocation) {
                logger.warn("Tentando fallback genérico...");
                targetUrl = String.format("%s/buscar?q=%s", BASE_URL, (cleanQuery + " " + year).replace(" ", "+"));
                doc = fetchDocument(targetUrl);
            }

            if (doc == null) return results;

            // --- 3. Extração dos Links via JSON-LD (O Pulo do Gato) ---
            // O site não tem <a> nos cards, mas tem um JSON no topo com as URLs.
            List<String> jsonLinks = extractLinksFromJsonLd(doc);
            
            // --- 4. Extração dos Cards Visuais ---
            // Usamos as classes que vimos no seu debug HTML
            Elements cards = doc.select("div.vehicle-card");
            
            logger.info("SóCarrão - Cards Visuais: {}, Links JSON: {}", cards.size(), jsonLinks.size());

            int count = Math.min(cards.size(), jsonLinks.size());
            for (int i = 0; i < count; i++) {
                try {
                    Element card = cards.get(i);
                    String link = jsonLinks.get(i); // Pega o link correspondente do JSON
                    
                    processCard(card, link, results);
                } catch (Exception e) {
                    logger.warn("Erro ao processar item {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Erro geral SóCarrão: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Extrai as URLs dos veículos do bloco JSON-LD (Schema.org)
     */
    private List<String> extractLinksFromJsonLd(Document doc) {
        List<String> links = new ArrayList<>();
        try {
            Elements scripts = doc.select("script[type='application/ld+json']");
            for (Element script : scripts) {
                String json = script.html();
                // Procura pelo bloco que é uma lista de itens ("@type":"ItemList")
                if (json.contains("ItemList") && json.contains("itemListElement")) {
                    // Regex simples para extrair URLs (evita dependência de parser JSON pesado)
                    Matcher m = Pattern.compile("\"url\":\"(https://www.socarrao.com.br/[^\"]+)\"").matcher(json);
                    while (m.find()) {
                        links.add(m.group(1));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao extrair JSON-LD: {}", e.getMessage());
        }
        return links;
    }

    private void processCard(Element card, String link, List<CarDTO> results) {
        // Título: Combina Marca + Modelo + Versão (Classes extraídas do seu HTML)
        String brand = card.select(".brand-model-formatter__brand").text();
        String model = card.select(".brand-model-formatter__model").text();
        String version = card.select(".vehicle-card__right--version").text();
        String title = (brand + " " + model + " " + version).trim();
        
        if (title.isEmpty()) title = card.select("h2, h3").text();

        // Preço: Classes vistas no debug
        String price = card.select(".vehicle-card__right--price .title-semibold").text();
        if (price.isEmpty()) price = card.select(".vehicle-card__priceSection--value .title-semibold").text();
        if (price.isEmpty()) price = "Sob Consulta";
        else if (!price.contains("R$")) price = "R$ " + price;

        // Especificações (Ano/KM): Estão numa <ul>
        // Ex: <li>2023/2024</li> ... <li>41.000km</li>
        Integer year = 2024;
        Integer km = 0;
        
        Elements specs = card.select(".vehicle-card__right--specs li");
        if (!specs.isEmpty()) {
            // Ano geralmente é o primeiro li
            String yearStr = specs.get(0).text(); 
            if (yearStr.contains("/")) yearStr = yearStr.split("/")[0];
            year = extractYearInt(yearStr);
            
            // KM geralmente é o terceiro li (índice 2) ou contém "km"
            for (Element spec : specs) {
                if (spec.text().toLowerCase().contains("km")) {
                    km = extractKm(spec.text());
                    break;
                }
            }
        }

        // Imagem
        String imgUrl = card.select("img").attr("src");
        if (imgUrl.isEmpty() || imgUrl.contains("data:image")) {
            imgUrl = card.select("img").attr("data-src");
        }

        // Localização
        String loc = card.select(".vehicle-card__left--location span").text();
        if (loc.isEmpty()) loc = card.select(".vehicle-card__right--location span").text();
        String source = "SóCarrão" + (loc.isEmpty() ? "" : " (" + loc + ")");

        results.add(new CarDTO(
                parser.sanitizeTitle(title),
                price,
                year,
                km,
                link,
                source,
                imgUrl
        ));
    }

    private Document fetchDocument(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .ignoreHttpErrors(true)
                    .timeout(15000)
                    .execute();

            if (response.statusCode() != 200) {
                saveDebugHtml(response.body(), response.statusCode());
                return null;
            }
            return response.parse();
        } catch (IOException e) {
            logger.error("Erro de rede: {}", e.getMessage());
        }
        return null;
    }

    private String extractYear(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(text);
        return m.find() ? m.group() : null;
    }

    private Integer extractYearInt(String text) {
        String y = extractYear(text);
        return y != null ? Integer.parseInt(y) : 2024;
    }
    
    private Integer extractKm(String text) {
        if (text == null) return 0;
        String clean = text.replaceAll("[^0-9]", "");
        return clean.isEmpty() ? 0 : Integer.parseInt(clean);
    }

    private void saveDebugHtml(String html, int statusCode) {
        try {
            File debugDir = new File("src/main/java/com/autoradar/debug");
            if (!debugDir.exists()) debugDir.mkdirs();
            
            File file = new File(debugDir, "debug_socarrao_" + statusCode + ".html");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(html);
            writer.close();
        } catch (IOException e) {
            logger.error("Falha ao salvar debug: {}", e.getMessage());
        }
    }

    @Override
    public String sourceName() {
        return "SóCarrão";
    }
}