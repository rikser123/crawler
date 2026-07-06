package rikser123.crawler.mapper;

import org.mapstruct.Mapper;
import rikser123.crawler.dto.MessageUserQueryDto;
import rikser123.crawler.dto.SearchResponseDto;

@Mapper(componentModel = "spring")
public interface SearchResponseMapper {
  SearchResponseDto mapToDtoFromMessage(MessageUserQueryDto.SearchResponse searchResponse);
}
