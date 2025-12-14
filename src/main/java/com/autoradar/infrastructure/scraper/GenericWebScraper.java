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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class GenericWebScraper implements CarScraperStrategy {

    private static final Logger logger = LoggerFactory.getLogger(GenericWebScraper.class);
    
    // User-Agent de navegador real para evitar bloqueios
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";

    private final CarDataParser parser;

    public GenericWebScraper(CarDataParser parser) {
        this.parser = parser;
    }

    @Override
    public List<CarDTO> search(String query, String location) {
        List<CarDTO> results = new ArrayList<>();
        
        // Estratégia de URL Genérica (deixa o ML redirecionar para a categoria certa)
        String term = (query + " " + location).trim().replace(" ", "-");
        String url = "https://lista.mercadolivre.com.br/" + term;

        try {
            logger.info("--- INÍCIO DO SCRAPING ---");
            logger.info("URL Alvo: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            String pageTitle = doc.title();
            logger.info("Título da Página: {}", pageTitle);

            // 1. Tenta identificar Layout NOVO (Poly)
            Elements items = doc.select("div.poly-card");
            if (!items.isEmpty()) logger.info("Detectado layout NOVO (Poly).");

            // 2. Se falhar, tenta Layout CLÁSSICO (Lista)
            if (items.isEmpty()) {
                items = doc.select("li.ui-search-layout__item");
                if (!items.isEmpty()) logger.info("Detectado layout CLÁSSICO (Lista).");
            }

            // 3. Se falhar, tenta Layout GRID (Grade)
            if (items.isEmpty()) {
                items = doc.select("div.ui-search-result__wrapper");
                if (!items.isEmpty()) logger.info("Detectado layout GRID.");
            }

            logger.info("Total de anúncios encontrados no HTML: {}", items.size());

            // --- DEBUG: Se não achou nada, pode ser bloqueio ou layout desconhecido ---
            if (items.isEmpty()) {
                logger.warn("NENHUM ITEM ENCONTRADO! Verifique se a página é um CAPTCHA.");
                // Se quiser ver o HTML no log para debug, descomente a linha abaixo:
                // logger.warn("HTML Snippet: {}", doc.html().substring(0, Math.min(500, doc.html().length())));
                return results;
            }

            for (Element item : items) {
                try {
                    // Seletores Híbridos (funcionam tanto no layout novo quanto antigo)
                    
                    // Título
                    String title = item.select("h2.poly-component__title, h2.ui-search-item__title, a.poly-component__title").text();
                    
                    // Link
                    String link = item.select("a.poly-component__title, a.ui-search-link").attr("href");
                    
                    // Preço
                    String price = item.select("span.andes-money-amount__fraction").text();
                    if (price.isEmpty()) price = "0";

                    // Imagem (tenta src normal e data-src do lazy load)
                    String imgUrl = item.select("img.poly-component__picture, img.ui-search-result-image__element").attr("src");
                    if (imgUrl.isEmpty() || imgUrl.contains("data:image")) {
                        imgUrl = item.select("img").attr("data-src");
                    }

                    // Validação mínima
                    if (!title.isEmpty() && !link.isEmpty()) {
                        results.add(new CarDTO(
                                parser.sanitizeTitle(title),
                                "R$ " + price,
                                2024, // Ano seria extraído dos atributos (simplificado por agora)
                                0,
                                link,
                                "Mercado Livre",
                                imgUrl
                        ));
                    }
                } catch (Exception e) {
                    logger.debug("Falha ao ler um item: {}", e.getMessage());
                }
            }

        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                logger.info("Página não encontrada (404). Termo de busca provavelmente inválido.");
                return new ArrayList<>();
            }
            logger.error("Erro HTTP {}: {}", e.getStatusCode(), e.getUrl());
        } catch (IOException e) {
            logger.error("Erro de Conexão: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public String sourceName() {
        return "Mercado Livre";
    }
}