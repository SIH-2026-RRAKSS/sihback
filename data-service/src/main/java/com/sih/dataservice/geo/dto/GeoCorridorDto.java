package com.sih.dataservice.geo.dto;

import java.util.List;

public class GeoCorridorDto {

    private String entityId;
    private String atmId;
    private String amount;
    private String fromCity;
    private List<Double> from;
    private String toCity;
    private List<Double> to;
    private String risk;

    public GeoCorridorDto() {
    }

    public GeoCorridorDto(String entityId, String atmId, String amount, String fromCity,
                          List<Double> from, String toCity, List<Double> to, String risk) {
        this.entityId = entityId;
        this.atmId = atmId;
        this.amount = amount;
        this.fromCity = fromCity;
        this.from = from;
        this.toCity = toCity;
        this.to = to;
        this.risk = risk;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAtmId() {
        return atmId;
    }

    public void setAtmId(String atmId) {
        this.atmId = atmId;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getFromCity() {
        return fromCity;
    }

    public void setFromCity(String fromCity) {
        this.fromCity = fromCity;
    }

    public List<Double> getFrom() {
        return from;
    }

    public void setFrom(List<Double> from) {
        this.from = from;
    }

    public String getToCity() {
        return toCity;
    }

    public void setToCity(String toCity) {
        this.toCity = toCity;
    }

    public List<Double> getTo() {
        return to;
    }

    public void setTo(List<Double> to) {
        this.to = to;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }
}
