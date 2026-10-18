package com.sparta.ourmarbleserver.game.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.sparta.ourmarbleserver.game.dto.CardData;
import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.dto.TileData;

import tools.jackson.databind.ObjectMapper;

/** 서버 기동 시 resources/data의 JSON 3개를 읽어 들고 있는다. */
@Service
public class GameDataService {

    private final List<PropertyData> properties;
    private final List<TileData> tiles;
    private final List<CardData> cards;

    public GameDataService(ObjectMapper objectMapper) {
        this.properties = read(objectMapper, "data/properties.json", PropertiesRoot.class).properties();
        this.tiles = read(objectMapper, "data/board.json", BoardRoot.class).tiles();
        this.cards = read(objectMapper, "data/cards.json", CardsRoot.class).cards();
    }

    public List<PropertyData> getProperties() { return properties; }
    public List<TileData> getTiles() { return tiles; }
    public List<CardData> getCards() { return cards; }

    private static <T> T read(ObjectMapper mapper, String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(path + "을 읽을 수 없습니다.", e);
        }
    }

    private record PropertiesRoot(List<PropertyData> properties) {}
    private record BoardRoot(int mapId, List<TileData> tiles) {}
    private record CardsRoot(List<CardData> cards) {}
}