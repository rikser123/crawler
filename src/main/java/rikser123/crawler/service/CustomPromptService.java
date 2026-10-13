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
import rikser123.crawler.mapper.CustomPromptMapper;
import rikser123.crawler.repository.CustomPromptRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPromptService {
  private final CustomPromptRepository customPromptRepository;
  private final UserDetailService userDetailService;
  private final CustomPromptMapper customPromptMapper;

  public RikserResponseItem<CustomPromptResponseDto> save(CustomPromptRequestDto requestDto) {
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
    var prompts = customPromptRepository.findAllByUserIdAndModeOrderByCreatedCreatedDesc(
      currentUser.getId(),
      filterDto.getMode()
    ).stream()
      .map(customPromptMapper::mapToDto)
      .toList();

    var response = new CustomPromptListResponseDto();
    response.setItems(prompts);

    return RikserResponseUtils.createResponse(response);
  }
}
