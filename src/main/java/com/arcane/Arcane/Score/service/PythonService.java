package com.arcane.Arcane.Score.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class PythonService {
    @Value("${modeling.python-url}")
    private String fastApiUrl;
    private final RestTemplate restTemplate;


    public Integer getRandomNumberFromPython(){
        return restTemplate.getForObject(fastApiUrl + "/random", Integer.class);
    }
}
