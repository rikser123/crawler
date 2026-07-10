package rikser123.crawler.mapper;

import org.mapstruct.Mapper;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;

@Mapper(componentModel = "spring")
public interface SearchResponseMapper {
  QueryResponseDto mapToDtoFromMessage(MessageUserQueryDto.SearchResponse searchResponse);
}
