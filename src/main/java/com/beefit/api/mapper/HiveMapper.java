package com.beefit.api.mapper;

import com.beefit.api.domain.Hive;
import com.beefit.api.dto.hive.HiveCreationDTO;
import com.beefit.api.dto.hive.HiveResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HiveMapper {

    Hive toEntity(HiveCreationDTO dto);

    @Mapping(source = "creator.id", target = "creatorId")
    HiveResponseDTO toDto(Hive hive);

}
