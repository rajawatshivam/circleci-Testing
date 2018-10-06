package com.stackroute.recommendation.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import com.stackroute.recommendation.domain.UserPreferences;

public interface UserPreferencesRepository extends Neo4jRepository<UserPreferences , Long>{

}
