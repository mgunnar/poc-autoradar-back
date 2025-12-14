package com.autoradar.infrastructure.scraper;

import com.autoradar.business.CarDataParser;
import com.autoradar.domain.dto.CarDTO;
import com.autoradar.domain.scraper.CarScraperStrategy;
import org.jsoup.HttpStatusException;
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

@Component
public class GenericWebScraper implements CarScraperStrategy {

    private static final Logger logger = LoggerFactory.getLogger(GenericWebScraper.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final CarDataParser parser;

    public GenericWebScraper(CarDataParser parser) {
        this.parser = parser;
    }

    @Override
    public List<CarDTO> search(String query, String location) {
        List<CarDTO> results = new ArrayList<>();
        String url = buildUrl(query, location);

        try {
            logger.info("URL Alvo: {}", url);
            
            Document doc = connect(url);
            saveDebugHtml(doc.html());

            if (isBlocked(doc.title())) {
                logger.error("BLOQUEIO DE ROBÔ DETECTADO");
                return results;
            }

            Elements items = findItemContainers(doc);
            logger.info("Total de anúncios encontrados: {}", items.size());

            for (Element item : items) {
                processItem(item, results);
            }

        } catch (HttpStatusException e) {
            handleHttpStatusException(e);
        } catch (IOException e) {
            logger.error("Erro de Conexão: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public String sourceName() {
        return "Mercado Livre";
    }

    private String buildUrl(String query, String location) {
        String term = query.trim().replace(" ", "-");
        String loc = location.trim().replace(" ", "-");

        if (loc.isEmpty() || loc.equalsIgnoreCase("sp") || loc.equalsIgnoreCase("brasil")) {
            return String.format("https://carros.mercadolivre.com.br/%s", term);
        }
        return String.format("https://carros.mercadolivre.com.br/%s/%s", loc, term);
    }

    private Document connect(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .timeout(15000)
                .followRedirects(true)
                .get();
    }

    private boolean isBlocked(String pageTitle) {
        return pageTitle.contains("CAPTCHA") || pageTitle.contains("segurança");
    }

    private Elements findItemContainers(Document doc) {
        Elements items = doc.select("li.ui-search-layout__item");
        if (items.isEmpty()) items = doc.select("div.ui-search-result__wrapper");
        if (items.isEmpty()) items = doc.select("div.andes-card");
        if (items.isEmpty()) items = doc.select("div.poly-card");
        return items;
    }

    private void processItem(Element item, List<CarDTO> results) {
        try {
            String title = item.select("a.poly-component__title, h2.ui-search-item__title").text();
            String link = item.select("a.poly-component__title, a.ui-search-link").attr("href");
            
            String price = item.select("span.andes-money-amount__fraction").text();
            if (price.isEmpty()) price = "0";

            String imgUrl = extractImageUrl(item);
            
            String attributes = item.select("li.poly-attributes_list__item, li.ui-search-card-attributes__attribute").text();
            
            Integer year = extractYear(attributes);
            Integer km = extractKm(attributes);

            if (!title.isEmpty() && !link.isEmpty()) {
                results.add(new CarDTO(
                        parser.sanitizeTitle(title),
                        "R$ " + price,
                        year,
                        km,
                        link,
                        "Mercado Livre",
                        imgUrl
                ));
            }
        } catch (Exception e) {
            logger.debug("Erro ao processar item: {}", e.getMessage());
        }
    }

    private String extractImageUrl(Element item) {
        String imgUrl = item.select("img.poly-component__picture, img.ui-search-result-image__element").attr("src");
        if (imgUrl.isEmpty() || imgUrl.contains("data:image")) {
            return item.select("img").attr("data-src");
        }
        return imgUrl;
    }

    private void handleHttpStatusException(HttpStatusException e) {
        if (e.getStatusCode() == 404) {
            logger.info("Página 404: Nenhum veículo encontrado nesta URL.");
        } else {
            logger.error("Erro HTTP {}: {}", e.getStatusCode(), e.getUrl());
        }
    }

    private Integer extractYear(String text) {
        if (text != null && text.matches(".*(19|20)\\d{2}.*")) {
            try {
                String yearStr = text.replaceAll(".*?((19|20)\\d{2}).*", "$1");
                return Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                return 2024;
            }
        }
        return 2024;
    }

    private Integer extractKm(String text) {
        if (text != null && text.toLowerCase().contains("km")) {
            try {
                String kmStr = text.replaceAll("[^0-9]", "");
                if (!kmStr.isEmpty()) {
                    int val = Integer.parseInt(kmStr);
                    if (val > 2030) return val; 
                }
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void saveDebugHtml(String html) {
        try {
            File file = new File("debug_last_scrape.html");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(html);
            writer.close();
            logger.info("DUMP RAW SALVO EM: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Falha ao salvar debug html: {}", e.getMessage());
        }
    }
}