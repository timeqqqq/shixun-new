package com.campus.qa.service;

import java.io.IOException;
import java.util.List;

public interface EmbeddingProvider {
    boolean isAvailable();

    String modelName();

    List<Double> embed(String text) throws IOException, InterruptedException;
}
