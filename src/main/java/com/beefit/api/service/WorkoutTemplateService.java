package com.beefit.api.service;

import com.beefit.api.domain.WorkoutTemplate;
import com.beefit.api.dto.WorkoutTemplateRequestDTO;
import com.beefit.api.dto.WorkoutTemplateResponseDTO;
import com.beefit.api.repository.WorkoutTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkoutTemplateService {

    @Autowired
    private WorkoutTemplateRepository WTRepository;

    @Transactional
    public WorkoutTemplateResponseDTO createTemplate(WorkoutTemplateRequestDTO requestDTO, UUID userID){
        WorkoutTemplate template = new WorkoutTemplate();
        template.setName(requestDTO.name());
        template.setMuscleGroupsList(requestDTO.muscleGroups());
        template.setExerciseList(requestDTO.exercises());
        template.setId(userID);

        WorkoutTemplate savedTemplate = WTRepository.save(template);

        return new WorkoutTemplateResponseDTO(
                savedTemplate.getId(),
                savedTemplate.getName(),
                savedTemplate.getMuscleGroupsList(),
                savedTemplate.getExerciseList()
        );
    }

    @Transactional
    public List<WorkoutTemplateResponseDTO> getUserTemplates(UUID userID){

        List<WorkoutTemplate> templates = WTRepository.findByUserId(userID);

        return templates.stream().map(t -> new WorkoutTemplateResponseDTO(
                t.getId(),
                t.getName(),
                t.getMuscleGroupsList(),
                t.getExerciseList())).toList();
    }


}
