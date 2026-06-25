package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.SearchResponseDtoWithChunks;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkSplitter {
  private final BlockingQueue<SearchResponseDtoWithContent> queue = new LinkedBlockingQueue<>();
  private final Executor executors = Executors.newVirtualThreadPerTaskExecutor();

  private static final String PARAGRAPH_BORDER = "\\n\\n+|\\n";
  private static final String SENTENCE_BORDER = "(?<=[.!?…])\\s+";
  private static final int CHUNK_GAP = 40;
  private static final int WORD_OVERLAP_COUNT = 20;

  private final FetchConfigProperties fetchConfigProperties;
  private final ApplicationEventPublisher eventPublisher;

  @PostConstruct
  void init() {
    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> split(request));
        }  catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
  }

  public void initSpliting(SearchResponseDtoWithContent responseDto) {
    queue.add(responseDto);
  }

  private void split(SearchResponseDtoWithContent searchResponse) {
    try {
      var text = searchResponse.getContent();
      if (StringUtils.isEmpty(text)) {
        log.warn("Переданный тест пустой! {}", searchResponse.getSearchResponse().getSearchResponseId());
         emitErrorEvent(searchResponse, null);
         return;
      }

      var chunks = new ArrayList<String>();
      var paragraphs = text.split(PARAGRAPH_BORDER);
      var chunkSize = fetchConfigProperties.getChunkSize();

      var currentChunk = new StringBuilder();
      var currentParagraphIndex = 0;

      while (currentParagraphIndex <= paragraphs.length - 1) {
        var trimmedParagraph = paragraphs[currentParagraphIndex].strip();

        if (trimmedParagraph.length() > chunkSize) {
          currentChunk = splitSentences(trimmedParagraph, currentChunk, chunks);
          currentParagraphIndex += 1;
          continue;
        }

        if (isChunkIsNotFull(currentChunk, trimmedParagraph)) {
          addToChunk(currentChunk, trimmedParagraph);
        } else {
          currentChunk = splitSentences(trimmedParagraph, currentChunk, chunks);
        }

        currentParagraphIndex += 1;
      }

      if (!currentChunk.isEmpty()) {
        chunks.add(currentChunk.toString());
      }

      var event = new FinishSplitChunksEvent();
      var eventDto = new SearchResponseDtoWithChunks();
      eventDto.setSearchResponse(searchResponse.getSearchResponse());
      eventDto.setChunks(chunks);
      event.setDtoWithChunks(eventDto);
      eventPublisher.publishEvent(event);
    } catch (Exception e) {
      log.warn("Error during splitting html", e);
      emitErrorEvent(searchResponse, e.getMessage());
    }

  }

  private StringBuilder splitSentences(String paragraph, StringBuilder currentChunk, List<String> chunks) {
    var sentences = paragraph.split(SENTENCE_BORDER);
    var chunk = currentChunk;
    var chunkSize = fetchConfigProperties.getChunkSize();

    for (var sentence : sentences) {
      var trimmedSentence = sentence.strip();
      if (isChunkIsNotFull(chunk, sentence)) {
        addToChunk(chunk, trimmedSentence);
      } else if (trimmedSentence.length() > chunkSize) {
          chunk = splitSentence(sentence, chunk, chunks);
      } else {
        chunks.add(chunk.toString());
        chunk = new StringBuilder();
        addToChunk(chunk, trimmedSentence);
      }
    }

    return chunk;
  }

  private StringBuilder splitSentence(String sentence, StringBuilder currentChunk, List<String> chunks) {
    var words = sentence.split(" ");
    var chunk = currentChunk;

    for (var word: words) {
      if (isChunkIsNotFull(chunk, word)) {
        addToChunk(chunk, word);
      }  else {
        chunks.add(chunk.toString());
        var chunkWords = Arrays.stream(chunk.toString().strip().split(" ")).toList();
        chunk = new StringBuilder();
        if (chunkWords.size() > WORD_OVERLAP_COUNT) {
          var overlappedWords = chunkWords.subList(chunkWords.size() - 1 - WORD_OVERLAP_COUNT, chunkWords.size() - 1);
          for (var overlapped: overlappedWords) {
            addToChunk(chunk, overlapped);
          }
        }
        addToChunk(chunk, word);
      }
    }

    return chunk;
  }

  private void addToChunk(StringBuilder chunk, String text) {
    if (chunk.isEmpty()) {
      chunk.append(text);
      return;
    }

    chunk.append(" " + text);
  }

  private boolean isChunkIsNotFull(StringBuilder chunk, String text) {
    var chunkSize = fetchConfigProperties.getChunkSize();
    return chunk.length() + text.length() - CHUNK_GAP < chunkSize;
  }

  private void emitErrorEvent(SearchResponseDtoWithContent searchResponse, @Nullable String errorMessage) {
    var errorEvent = new ResponseProcessingErrorEvent();
    errorEvent.setSearchResponseId(searchResponse.getSearchResponse().getSearchResponseId());
    errorEvent.setUrl(searchResponse.getSearchResponse().getUrl());
    errorEvent.setMessage("Не удалось разрезать текст на чанки" + StringUtils.defaultIfEmpty(errorMessage, StringUtils.EMPTY));
    eventPublisher.publishEvent(errorEvent);
  }
}
