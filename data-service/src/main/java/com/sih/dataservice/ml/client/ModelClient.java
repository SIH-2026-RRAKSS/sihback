package com.sih.dataservice.ml.client;

import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;

public interface ModelClient {

    PredictionResponse predict(PredictionRequest request);

    boolean isAvailable();
}
