package com.beefit.api.mapper;

import com.beefit.api.domain.Hive;
import com.beefit.api.dto.HiveCreationDTO;
import com.beefit.api.dto.HiveResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HiveMapper {

    Hive toEntity(HiveCreationDTO dto);

    HiveResponseDTO toDto(Hive hive);

}
