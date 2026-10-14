package com.beefit.api.infra.database;

import com.beefit.api.domain.CatalogExercise;
import com.beefit.api.domain.enums.MuscleGroups;
import com.beefit.api.domain.enums.AnatomicalMuscle;
import com.beefit.api.dto.seed.ExerciseSeedDTO;
import com.beefit.api.dto.seed.ExerciseSeedFileDTO;
import com.beefit.api.exception.exercises.DatabaseSeederFailException;
import com.beefit.api.repository.CatalogExerciseRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "beefit.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSeeder implements CommandLineRunner { // Interface faz a classe rodar na inciciação

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final ObjectProvider<CatalogExerciseRepository> catalogExerciseRepositoryProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseSeeder(ObjectProvider<CatalogExerciseRepository> exerciseRepositoryProvider) {
        this.catalogExerciseRepositoryProvider = exerciseRepositoryProvider;
    }

    @Override
    // Função principal
    public void run(String... args) throws Exception {
        catalogExerciseFactory();
    }

    public void catalogExerciseFactory(){
        CatalogExerciseRepository catalogExerciseRepository = catalogExerciseRepositoryProvider.getIfAvailable();

        if (catalogExerciseRepository == null) {
            return;
        }

        ClassPathResource resource = new ClassPathResource("static/exercises/exercisesv5.json");

        try(InputStream inputStream = resource.getInputStream() /* Passa do Json para bytes */){
            // Cria lista de ExerciseSeedDTO a partir dos Json
            ExerciseSeedFileDTO seedFile = objectMapper.readValue(
                    inputStream,
                    ExerciseSeedFileDTO.class
            );

            List<ExerciseSeedDTO> seeds = seedFile.getExercises();

            if(catalogExerciseRepository.count() == 0){
                LOGGER.info("Catálogo vazio; iniciando carga inicial de exercícios.");

                // Gera lista vazia de exercícios
                List<CatalogExercise> exercises = new ArrayList<>();

                for(ExerciseSeedDTO seed : seeds){

                    CatalogExercise exercise = new CatalogExercise();
                    String fullName = seed.getName().substring(0,1).toUpperCase() + seed.getName().substring(1);
                    exercise.setName(fullName);
                    exercise.setImages(seed.getImages());

                    if (seed.getInstructions() != null && !seed.getInstructions().isEmpty()) {
                        // Junta todos os passos quebando a linha (\n) para ficar formatado
                        String fullDescription = String.join("\n", seed.getInstructions());

                        // Garante que nao vai ser maior que 1000 caracteres
                        if (fullDescription.length() > 1000) {
                            fullDescription = fullDescription.substring(0, 997) + "...";
                        }

                        exercise.setInstructions(fullDescription);
                    }

                    List<MuscleGroups> primaryGroups  = seed.getPrimaryMuscleGroupList().stream()
                            .map(MuscleGroups::fromString)
                            .toList();

                    List<MuscleGroups> secondaryGroups  = seed.getSecondaryMuscleGroupList().stream()
                            .map(MuscleGroups::fromString)
                            .toList();

                    exercise.setPrimaryMuscleGroupsList(primaryGroups);
                    exercise.setSecondaryMuscleGroupsList(secondaryGroups);
                    exercise.setPrimaryMusclesList(toAnatomicalMuscles(seed.getPrimaryMuscleGroupList()));
                    exercise.setSecondaryMusclesList(toAnatomicalMuscles(seed.getSecondaryMuscleGroupList()));

                    // Adiciona o exercício criado à lista
                    exercises.add(exercise);
                }

                // Salva todos exercícios de uma vez
                catalogExerciseRepository.saveAll(exercises);

                LOGGER.info("Carga inicial de exercícios concluída.");
            } else {
                backfillAnatomicalMuscles(catalogExerciseRepository, seeds);
            }
        } catch (IOException | IllegalArgumentException e){
            throw new DatabaseSeederFailException("Não foi possível carregar os dados dos exercícios para o banco de dados.", e);
        }
    }

    private List<AnatomicalMuscle> toAnatomicalMuscles(List<String> seedValues) {
        return seedValues == null ? List.of() : seedValues.stream()
                .map(AnatomicalMuscle::fromSeedValue)
                .distinct()
                .toList();
    }

    private void backfillAnatomicalMuscles(
            CatalogExerciseRepository catalogExerciseRepository,
            List<ExerciseSeedDTO> seeds) {
        Map<String, ExerciseSeedDTO> seedsByName = new HashMap<>();
        for (ExerciseSeedDTO seed : seeds) {
            seedsByName.put(capitalize(seed.getName()), seed);
        }

        List<CatalogExercise> changedExercises = new ArrayList<>();
        for (CatalogExercise exercise : catalogExerciseRepository.findAll()) {
            if (exercise.getPrimaryMusclesList() != null && !exercise.getPrimaryMusclesList().isEmpty()) continue;
            ExerciseSeedDTO seed = seedsByName.get(exercise.getName());
            if (seed == null) continue;
            exercise.setPrimaryMusclesList(toAnatomicalMuscles(seed.getPrimaryMuscleGroupList()));
            exercise.setSecondaryMusclesList(toAnatomicalMuscles(seed.getSecondaryMuscleGroupList()));
            changedExercises.add(exercise);
        }

        if (!changedExercises.isEmpty()) {
            catalogExerciseRepository.saveAll(changedExercises);
            LOGGER.info("Músculos anatômicos preenchidos em {} exercícios existentes.", changedExercises.size());
        }
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
