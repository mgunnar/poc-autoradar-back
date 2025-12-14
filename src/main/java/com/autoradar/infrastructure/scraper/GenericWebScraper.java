package com.autoradar.infrastructure.scraper;

import com.autoradar.business.CarDataParser;
import com.autoradar.domain.dto.CarDTO;
import com.autoradar.domain.scraper.CarScraperStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class GenericWebScraper implements CarScraperStrategy {

    private static final Logger logger = LoggerFactory.getLogger(GenericWebScraper.class);
    
    // Simula um navegador real para evitar bloqueios simples
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    private final CarDataParser parser;

    public GenericWebScraper(CarDataParser parser) {
        this.parser = parser;
    }

    @Override
    public List<CarDTO> search(String query, String location) {
        // Monta a URL no padrão do Mercado Livre
        // Ex: https://lista.mercadolivre.com.br/veiculos/civic-sp
        String url = String.format("https://lista.mercadolivre.com.br/veiculos/", 
                query.trim().replace(" ", "-"), 
                location.trim().replace(" ", "-"));
        
        List<CarDTO> results = new ArrayList<>();

        try {
            logger.info("Buscando no Mercado Livre: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "pt-BR")
                    .timeout(10000)
                    .get();

            // Seletores CSS específicos do Mercado Livre (Lista de resultados)
            Elements items = doc.select("li.ui-search-layout__item");
            
            if (items.isEmpty()) {
                // Tenta seletor alternativo (grid view)
                items = doc.select("div.ui-search-result__wrapper");
            }

            logger.info("Encontrados {} itens brutos.", items.size());

            for (Element item : items) {
                try {
                    // Extração de Título
                    String title = item.select("h2.ui-search-item__title, h2.poly-component__title").text();
                    
                    // Extração de Link
                    String link = item.select("a.ui-search-link, a.poly-component__title").attr("href");
                    
                    // Extração de Preço (pode vir picado no HTML)
                    String price = item.select("span.andes-money-amount__fraction").first() != null 
                            ? "R$ " + item.select("span.andes-money-amount__fraction").first().text()
                            : "Sob Consulta";
                    
                    // Extração de Imagem (Lazy loading usa data-src as vezes)
                    String imgUrl = item.select("img.ui-search-result-image__element, img.poly-component__picture").attr("src");
                    if (imgUrl.isEmpty()) {
                        imgUrl = item.select("img").attr("data-src");
                    }

                    // Tenta extrair o Ano do texto de atributos (ex: "2018 | 50.000 km")
                    String attributes = item.select("li.ui-search-card-attributes__attribute, span.poly-attributes-list__item").text();
                    Integer year = extractYear(attributes);

                    // Limpeza e validação
                    String cleanTitle = parser.sanitizeTitle(title);
                    
                    if (!cleanTitle.isEmpty()) {
                        results.add(new CarDTO(
                                cleanTitle,
                                price,
                                year,
                                0, // KM seria extraído do atributo também
                                link,
                                "Mercado Livre",
                                imgUrl
                        ));
                    }
                } catch (Exception e) {
                    // Ignora item com erro e vai pro próximo
                }
            }

        } catch (IOException e) {
            logger.error("Erro ao acessar Mercado Livre: {}", e.getMessage());
        }
        
        return results;
    }

    private Integer extractYear(String text) {
        // Procura por 4 dígitos que pareçam um ano (19xx ou 20xx)
        if (text != null && text.matches(".*(19|20)\\d{2}.*")) {
            String yearStr = text.replaceAll(".*?((19|20)\\d{2}).*", "$1");
            return Integer.parseInt(yearStr);
        }
        return 9999; // Valor padrão se não achar
    }

    @Override
    public String sourceName() {
        return "Mercado Livre";
    }
}