package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithChunks;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkSplitter {
  private static final String PARAGRAPH_BORDER = "\\n\\n+|\\n";
  private static final String SENTENCE_BORDER = "(?<=[.!?…])\\s+";
  private static final int CHUNK_GAP = 40;

  private final FetchConfigProperties fetchConfigProperties;

  public SearchResponseDtoWithChunks split(SearchResponseDtoWithContent searchResponse) {
    try {
      var chunkSize = fetchConfigProperties.getChunkSize();
      var overlapCount = fetchConfigProperties.getWordOverlapCount();
      var text = searchResponse.getContent();
      var chunks = new ArrayList<String>();

      if (StringUtils.isEmpty(text)) {
        log.warn("Переданный тест пустой! {}", searchResponse.getSearchResponse().getSearchResponseId());
        throw new IllegalStateException("Переданный тест пустой!");
      }

      if (text.length() < chunkSize + overlapCount) {
        chunks.add(text);
        return getSplitChunksDto(searchResponse.getSearchResponse(), chunks);
      }

      var paragraphs = text.split(PARAGRAPH_BORDER);

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

      return getSplitChunksDto(searchResponse.getSearchResponse(), chunks);
    } catch (Exception e) {
      log.warn("Error during splitting html", e);
      throw new IllegalStateException("Не удалось разрезать текст на чанки ");
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
    var wordOverlapCount = fetchConfigProperties.getWordOverlapCount();
    var words = sentence.split(" ");
    var chunk = currentChunk;

    for (var word: words) {
      if (isChunkIsNotFull(chunk, word)) {
        addToChunk(chunk, word);
      }  else {
        chunks.add(chunk.toString());
        var chunkWords = Arrays.stream(chunk.toString().strip().split(" ")).toList();
        chunk = new StringBuilder();
        if (chunkWords.size() > wordOverlapCount) {
          var overlappedWords = chunkWords.subList(chunkWords.size() - 1 - wordOverlapCount, chunkWords.size() - 1);
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

  private SearchResponseDtoWithChunks getSplitChunksDto(QueryResponseDto queryResponseDto, List<String> chunks) {
    var dto = new SearchResponseDtoWithChunks();
    dto.setSearchResponse(queryResponseDto);
    dto.setChunks(chunks);
    return dto;
  }
}
