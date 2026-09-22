package com.sih.dataservice.ml.dto;

public class TopNodeExplanation {

    private String id;
    private double score;

    public TopNodeExplanation() {
    }

    public TopNodeExplanation(String id, double score) {
        this.id = id;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
