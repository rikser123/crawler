package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.PrometheusMetrics;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.userQuery.QueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryDto;
import rikser123.crawler.mapper.UserQueryMapper;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueryProcessor {
  private final Crawler crawler;
  private final TextExtractor textExtractor;
  private final ChunkSplitter chunkSplitter;
  private final Summariser summariser;
  private final QueryAnalizer queryAnalizer;
  private final SearchResponseMessageService searchResponseMessageService;
  private final UserQueryMapper userQueryMapper;
  private final SearchQueryMessageService searchQueryMessageService;
  private final FetchConfigProperties fetchConfigProperties;
  private final PrometheusMetrics prometheusMetrics;

  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();
  private BlockingQueue<UserQueryDto> queue = new LinkedBlockingQueue<>();
  private Semaphore semaphore;


  @PostConstruct
  void init() {
    semaphore = new Semaphore(fetchConfigProperties.getQueueLimit());

    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> processUserQuery(request));
        }  catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
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

  public void initProcessing(MessageUserQueryDto messageDto) {
    var userQueryDto = userQueryMapper.mapMessageToDto(messageDto);
    queue.add(userQueryDto);

  }

  private void processUserQuery(UserQueryDto userQueryDto) {
    prometheusMetrics.incrementSearchQuery();

    var responses = userQueryDto.getSearchResponses()
      .stream()
      .map(SearchResponseDtoWithContent::getSearchResponse)
      .toList();
   var futures = responses.stream().map(this::processUserSearchResponse).toList();
   CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

   var results = futures.stream().map(CompletableFuture::join).toList();
   var allFailed = results.stream().allMatch(Objects::isNull);

   if (allFailed) {
     handleFailedQueries(userQueryDto, "Обработка всех ответов от яндекса завершился ошибкой!");
     return;
   }

   var texts = results.stream().filter(StringUtils::isNotEmpty).toList();
   var queryDto = new QueryAnalysisDto();
   queryDto.setQueryText(userQueryDto.getQueryText());
   queryDto.setSearchQueryId(userQueryDto.getSearchQueryId());
   queryDto.setTexts(texts);
   queryDto.setUserId(userQueryDto.getUserId());

   SearchQueryOutboxMessage message;

   try {
     var result = queryAnalizer.makeAnalysis(queryDto);
     message = searchQueryMessageService.createQueryOutboxSuccessMessage(result);

     prometheusMetrics.incrementSuccessQuery();
   } catch (Exception e) {
     log.warn("Не удалось обработать пересказы", e);
     var analysisDto = new UserQueryAnalysisDto();
     analysisDto.setUserId(queryDto.getUserId());
     analysisDto.setSearchQueryId(queryDto.getSearchQueryId());
     message = searchQueryMessageService.createQueryOutboxErrorMessage(analysisDto, "Не удалось обработать пересказы!");

     prometheusMetrics.incrementFailQuery();
   }
   searchQueryMessageService.save(message);
  }

  private CompletableFuture<String> processUserSearchResponse(QueryResponseDto response) {
    prometheusMetrics.incrementQueryResponse();

    var acquired = new AtomicBoolean();
    return CompletableFuture.supplyAsync(() -> {
        try {
          semaphore.acquire();
          acquired.set(true);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return crawler.download(response);
    },executors)
      .thenApply(textExtractor::extractText)
      .thenApply(chunkSplitter::split)
      .thenApply(summariser::summarise)
      .orTimeout(90, TimeUnit.SECONDS)
      .handle((result, error) -> {
        if (acquired.get()) {
          semaphore.release();
        }
        if (error != null || result == null) {
          prometheusMetrics.incrementFailResponse();

          searchResponseMessageService.createOutboxRequestError(
            response.getSearchResponseId(),
            error.getMessage()
          );
          return null;
        }

        return result.getContent() + " Источник: " + result.getSearchResponse().getUrl();
      });
  }


  private void handleFailedQueries(UserQueryDto query, String errorMessage) {
    var analysisDto = new UserQueryAnalysisDto();
    analysisDto.setUserId(query.getUserId());
    analysisDto.setSearchQueryId(query.getSearchQueryId());
    var message = searchQueryMessageService.createQueryOutboxErrorMessage(
      analysisDto,
      errorMessage
    );
    searchQueryMessageService.save(message);
  }
}
