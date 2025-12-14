
package com.autoradar.domain.scraper;

import com.autoradar.domain.dto.CarDTO;
import java.util.List;

public interface CarScraperStrategy {
    List<CarDTO> search(String query, String location);
    String sourceName();
}
