package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.game.dto.*;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import com.sparta.ourmarbleserver.property.dto.*;
import lombok.RequiredArgsConstructor;
import org.aspectj.bridge.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class GameMessageHandler implements WsMessageHandler{
    private final GameService gameService;
    private final WebSocketMessageSender webSocketMessageSender;
    private final LobbyService lobbyService;

    @Override
    public Set<MessageType> types() {
        return Set.of(
                MessageType.BUILD,
                MessageType.DRAW_CARD,
                MessageType.CHOOSE_DESTINATION,
                MessageType.ROLL_DICE,
                MessageType.SELL_PROPERTIES,
                MessageType.ACQUIRE_PROPERTY,
                MessageType.PURCHASE_PROPERTY,
                MessageType.GAME_STATE_REQUEST
        );
    }

    @Override
    public void handle(MessageType requestType, long playerId, JsonNode payload) {

        ObjectMapper objectMapper = new ObjectMapper();
        GameResult gameResult;

        switch (requestType) {
            case BUILD -> {
                BuildRequestPayload buildRequestPayload = objectMapper.treeToValue(payload, BuildRequestPayload.class);
                gameResult = gameService.build(playerId, buildRequestPayload.propertyId(), buildRequestPayload.isAccept());
            }
            case DRAW_CARD -> {
                gameResult = gameService.drawCard(playerId);
            }
            case CHOOSE_DESTINATION -> {
                ChooseDestinationRequestPayload chooseDestinationRequestPayload = objectMapper.treeToValue(payload, ChooseDestinationRequestPayload.class);
                gameResult = gameService.chooseDestination(playerId, chooseDestinationRequestPayload.destinationPosition());
            }
            case ROLL_DICE -> {
                gameResult = gameService.rollDice(playerId);
            }
            case SELL_PROPERTIES -> {
                SellPropertiesRequestPayload sellPropertiesRequestPayload = objectMapper.treeToValue(payload, SellPropertiesRequestPayload.class);
                gameResult = gameService.sellProperties(playerId, sellPropertiesRequestPayload.propertyIds());
            }
            case ACQUIRE_PROPERTY -> {
                AcquirePropertyRequestPayload acquirePropertyRequestPayload = objectMapper.treeToValue(payload, AcquirePropertyRequestPayload.class);
                gameResult = gameService.acquireProperty(playerId, acquirePropertyRequestPayload.propertyId(), acquirePropertyRequestPayload.isAccept());
            }
            case PURCHASE_PROPERTY -> {
                PurchasePropertyRequestPayload purchasePropertyRequestPayload = objectMapper.treeToValue(payload, PurchasePropertyRequestPayload.class);
                gameResult = gameService.purchaseProperty(playerId, purchasePropertyRequestPayload.propertyId(), purchasePropertyRequestPayload.isAccept());
            }
            case GAME_STATE_REQUEST -> {
                Optional<GameMessage> stateResult = gameService.syncState(playerId);
                GameMessage gameMessage = stateResult.orElse(null);

                if(gameMessage == null)
                    webSocketMessageSender.sendToPlayer(playerId, MessageType.GAME_STATE_SYNC, null);
                else
                    webSocketMessageSender.sendToPlayer(playerId, MessageType.GAME_STATE_SYNC, withRoomInfo(gameMessage.payload()));
                return;
            }
            default -> throw new IllegalStateException("GameMessageHandler가 처리하지 않는 request type 입니다. type : " + requestType);
        }

        String roomId = gameResult.roomId();
        for(GameMessage gameMessage : gameResult.messages())
        {
            webSocketMessageSender.sendToRoom(roomId, gameMessage.type(), gameMessage.payload());
        }
    }

    // 게임 상태에는 방장과 최대 인원이 없어서, 그 방(로비)에서 읽어 붙인다. 방을 못 찾으면 붙이지 않는다. (클라가 플레이어 수로 대신 표시)
    private Object withRoomInfo(Object payload) {
        if (!(payload instanceof GameStateSyncPayload sync)) {
            return payload;
        }
        return lobbyService.findRoomInfo(sync.gameState().roomId())
                .<Object>map(room -> sync.withRoom(room.hostId(), room.maxPlayers()))
                .orElse(sync);
    }
}
