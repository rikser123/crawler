package rikser123.crawler.service;

public interface PipelineStep<T> {
  void initProcessing(T request);

}
