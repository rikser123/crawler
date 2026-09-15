package rikser123.crawler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.llm.LlmClusters;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.userQuery.QueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalizer {
  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

  private final LlmService llmService;
  private ObjectMapper objectMapper;

  @PostConstruct
  void init() {
    objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
  }

  @PreDestroy
  void preDestroy() {
    executors.shutdown();
    try {
      if (executors.awaitTermination(30, TimeUnit.SECONDS)) {
        executors.shutdownNow();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  public UserQueryAnalysisDto makeAnalysis(QueryAnalysisDto request) {
    try {
      var userQuery = request.getQueryText();
      var contents = request.getContents();

      var clusters = llmService.getClusters(userQuery, contents);
      var clustersMap = groupSummariesByClusters(clusters, contents);

      var futures = clustersMap.entrySet().stream().map(entry -> {
        try {
          var clusterStrung = objectMapper.writeValueAsString(entry.getKey());
          var values = entry.getValue();
          return processClusters(userQuery, clusterStrung, values);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
      }).toList();
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
      var clustersData = futures.stream().map(CompletableFuture::join).toList();

      var synthesis = llmService.getClustersSynthesis(userQuery, clustersData);
      var critics = llmService.getCritic(userQuery, synthesis);
      var analysis = llmService.getAnalysis(userQuery, synthesis, critics, clustersData);

      return createAnalysisDto(request, analysis);

    } catch (IllegalStateException | JsonProcessingException exception) {
      log.warn("Не удалось получить анализ данных от модели", exception);
      throw new IllegalStateException("Не удалось получить анализ данных от модели");
    }
  }

  private CompletableFuture<String> processClusters(
    String userQuery,
    String cluster,
    List<SearchResponseDtoWithContent> contents
  ) {
    return CompletableFuture.supplyAsync(
      () -> llmService.getAnalysisCluster(userQuery, cluster, contents),
      executors
    ).handle((result, error) -> {
      if (error != null) {
        return null;
      }

      return result;
    });
  }

  private UserQueryAnalysisDto createAnalysisDto(QueryAnalysisDto request, String analysis) {
    var dto = new UserQueryAnalysisDto();
    dto.setUserId(request.getUserId());
    dto.setSearchQueryId(request.getSearchQueryId());
    dto.setAnalysis(analysis);
    return dto;
  }

  private Map<LlmClusters.Cluster, List<SearchResponseDtoWithContent>> groupSummariesByClusters(
    String clustersJson,
    List<SearchResponseDtoWithContent> contents
  ) throws JsonProcessingException {
      var clustersDto = objectMapper.readValue(clustersJson.replaceAll("(?s)^\\s*```json\\s*(.*?)\\s*```\\s*$", "$1"), LlmClusters.class);
      var clusters = clustersDto.getClusters();

      return clusters.stream().collect(Collectors.toMap(x -> x, cluster -> {
        var relatedContents = contents.stream()
          .filter(content -> cluster.getSources().contains(content.getSearchResponse().getUrl()))
          .toList();
        return relatedContents;
      }));

  }
}
