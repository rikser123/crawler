package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.userQuery.QueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalizer {
  private final DeepSeekService bothubService;

  public UserQueryAnalysisDto makeAnalysis(QueryAnalysisDto request) {
    try {
      var userQuery = request.getQueryText();
      var summaries = request.getTexts();
      var response = bothubService.getQueryAnalysis(userQuery, summaries);

      return createAnalysisDto(request, response);

    } catch (IllegalStateException exception) {
      log.warn("Не удалось получить анализ данных от модели", exception);
      throw new IllegalStateException("Не удалось получить анализ данных от модели");
    }
  }

  private UserQueryAnalysisDto createAnalysisDto(QueryAnalysisDto request, String analysis) {
    var dto = new UserQueryAnalysisDto();
    dto.setUserId(request.getUserId());
    dto.setSearchQueryId(request.getSearchQueryId());
    dto.setAnalysis(analysis);
    return dto;
  }
}
