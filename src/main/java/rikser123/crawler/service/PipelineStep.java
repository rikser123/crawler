package rikser123.crawler.service;

import rikser123.crawler.dto.SearchResponseDto;

public interface PipelineStep<T> {
  void initProcessing(T request);

}
