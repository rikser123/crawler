package rikser123.crawler.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rikser123.bundle.dto.User;
import rikser123.bundle.dto.response.RikserResponseItem;
import rikser123.bundle.service.UserDetailService;
import rikser123.bundle.utils.RikserResponseUtils;
import rikser123.crawler.dto.customPrompt.CustomPromptFilterDto;
import rikser123.crawler.dto.customPrompt.CustomPromptListResponseDto;
import rikser123.crawler.dto.customPrompt.CustomPromptRequestDto;
import rikser123.crawler.dto.customPrompt.CustomPromptResponseDto;
import rikser123.crawler.dto.customPrompt.CustomPromptSuggestionResponseDto;
import rikser123.crawler.mapper.CustomPromptMapper;
import rikser123.crawler.repository.CustomPromptRepository;
import rikser123.crawler.repository.entity.CustomPromptMode;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPromptService {
  private static final String PROMPT_PARAMETER_PLACEHOLDER = "%s";
  private static final int SHORT_MODE_PLACEHOLDER_COUNT = 2;
  private static final int LONG_MODE_PLACEHOLDER_COUNT = 4;

  private final CustomPromptRepository customPromptRepository;
  private final UserDetailService userDetailService;
  private final CustomPromptMapper customPromptMapper;
  private final LlmService llmService;

  public RikserResponseItem<CustomPromptSuggestionResponseDto> getPromptSuggestion(CustomPromptRequestDto requestDto) {
    validatePrompt(requestDto);
    var suggestionPrompt = llmService.getSuggestionPrompt(requestDto.getText(), requestDto.getMode());

    var response = new CustomPromptSuggestionResponseDto();
    response.setPrompt(suggestionPrompt);
    return RikserResponseUtils.createResponse(response);
  }

  public RikserResponseItem<CustomPromptResponseDto> save(CustomPromptRequestDto requestDto) {
    validatePrompt(requestDto);
    var entity = customPromptMapper.mapToEntity(requestDto);
    var saved = customPromptRepository.save(entity);
    var response = customPromptMapper.mapToDto(saved);

    return RikserResponseUtils.createResponse(response);
  }

  public CustomPromptResponseDto findById(UUID id) {
    var prompt = customPromptRepository.findById(id).orElseThrow(() ->
      new EntityNotFoundException("Не удалось найти промп с id " + id));
    return customPromptMapper.mapToDto(prompt);
  }

  public RikserResponseItem<CustomPromptListResponseDto> getUserPrompts(CustomPromptFilterDto filterDto) {
    var currentUser = (User) userDetailService.getCurrentUser();
    var prompts = customPromptRepository.findAllByUserIdAndModeOrderByCreatedDesc(
      currentUser.getId(),
      filterDto.getMode()
    ).stream()
      .map(customPromptMapper::mapToDto)
      .toList();

    var response = new CustomPromptListResponseDto();
    response.setItems(prompts);

    return RikserResponseUtils.createResponse(response);
  }

  private void validatePrompt(CustomPromptRequestDto requestDto) {
    var mode = requestDto.getMode();
    var text = requestDto.getText();
    var placeholderCount = countPlaceholderLength(text);

    if ((mode == CustomPromptMode.SHORT && placeholderCount != SHORT_MODE_PLACEHOLDER_COUNT)
      || (mode == CustomPromptMode.LONG && placeholderCount != LONG_MODE_PLACEHOLDER_COUNT)) {
      throw new IllegalStateException("Неверный формат касмтомного промпта");
    }
  }

  private int countPlaceholderLength(String text) {
    var count = 0;
    var index = text.indexOf(PROMPT_PARAMETER_PLACEHOLDER);

    while (index != -1) {
      count +=1;
      index = text.indexOf(PROMPT_PARAMETER_PLACEHOLDER, index + PROMPT_PARAMETER_PLACEHOLDER.length());

    }

    return count;
  }
}
