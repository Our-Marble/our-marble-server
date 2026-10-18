package com.sparta.ourmarbleserver.game.service;

import org.springframework.stereotype.Service;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import lombok.RequiredArgsConstructor;

/**
 * 말이 칸에 도착한 뒤의 처리: 칸 종류에 따라 다음 phase를 정하고, 통행료 정산과 파산을 한다. (클라 ProcessArrival)
 * 상태만 바꾸고, 요청 검증, 저장, 알림, 요청 락은 GameService가 한다.
 * 같은 패키지에서만 보이게 해서 GameService를 거치지 않고는 부를 수 없다. (요청 락을 우회하지 못하게)
 */
@Service
@RequiredArgsConstructor
class ArrivalService {

    private final TurnService turnService;
    private final EconomyService economyService;
    private final PropertyService propertyService;
    private final GameDataService gameDataService;

    /** 도착한 칸에 따라 다음 phase를 정한다. 이동(주사위, 세계여행, 이동 카드)이 끝난 뒤 불린다. */
    void processArrival(GameState state, PlayerState player) {
        TileData tile = tileAt(player.getPosition());
        switch (tile.type()) {
            case "PROPERTY" -> arriveAtProperty(state, player, tile.propertyId());
            case "GOLDEN_KEY" -> state.setPhase(TurnPhase.AWAITING_DRAW_CARD);
            case "START" -> turnService.endTurn(state); //효과 없음(월급은 이동할 때 이미 지급됨)
            case "CHARITY" -> {
                economyService.receiveWelfareFund(state, player);
                turnService.endTurn(state);
            }
            case "DONATION" -> {
                economyService.payTax(state, player, state.getConfig().taxAmount());
                turnService.endTurn(state);
            }
            case "WORLD_TRAVEL" -> turnService.passTurn(state); // 더블이어도 강제로 턴을 넘긴다.
            case "ISLAND" -> {
                turnService.imprison(state, player);
                turnService.passTurn(state); // 강제로 턴을 넘긴다.
            }
            default -> turnService.endTurn(state);
        }
    }

    private void arriveAtProperty(GameState state, PlayerState player, int propertyId) {
        PropertyState property = state.getPropertyState(propertyId)
                .orElseThrow(() -> new IllegalStateException("땅 상태가 없습니다." + propertyId));

        if (!property.hasOwner()) {
            state.setPhase(TurnPhase.AWAITING_PURCHASE);
        } else if (property.isOwnedBy(player.getPlayerId())) {
            state.setPhase(TurnPhase.AWAITING_BUILD);
        } else {
            settleToll(state, player, property);
        }
    }

    /**
     * 남의 땅에 도착했을 때 통행료를 내고, 인수할 수 있는지에 따라 다음 phase를 정한다.
     * 통행료와 현금 이체는 알림 없이 서버 내부에서만 처리한다. (클라가 같은 규칙으로 계산)
     * 통행료를 내고도 현금 ≥ 인수가이면 인수 선택을 기다리고, 아니면 턴을 끝낸다.
     */
    private void settleToll(GameState state, PlayerState payer, PropertyState property) {
        long toll = propertyService.getToll(property);
        if (payer.getMoney() < toll) {
            long sellable = propertyService.getTotalSellValue(state, payer.getPlayerId());
            if (payer.getMoney() + sellable >= toll) {
                state.setPhase(TurnPhase.AWAITING_SELL);
            } else {
                declareBankrupt(state, payer, state.getPlayerState(property.getOwnerId()));
            }
            return;
        }

        PlayerState owner = state.getPlayerState(property.getOwnerId());
        economyService.transfer(payer, owner, toll);

        if (payer.getMoney() >= propertyService.getAcquireValue(property)) {
            state.setPhase(TurnPhase.AWAITING_ACQUIRE);
        } else {
            turnService.endTurn(state);
        }
    }

    /**
     * 통행료를 낼 수 없을 때(전부 팔아도 부족) 파산 처리한다. (클라 HandleBankruptcy와 같은 규칙)
     * 가진 땅을 모두 매각가로 현금화하고, 현금 전액을 수납자에게 넘기고, 땅은 주인 없음 + 건설 단계 0으로 초기화한다.
     * 파산은 클라에 알리지 않고 서버 내부에서만 처리한다. 더블이어도 턴은 다음 플레이어로 넘어간다.
     * 게임이 끝나면(생존자 1명) turnService.passTurn은 아무것도 바꾸지 않는다.
     */
    private void declareBankrupt(GameState state, PlayerState payer, PlayerState receiver) {
        long payerId = payer.getPlayerId();
        long liquidated = propertyService.getTotalSellValue(state, payerId);
        state.properties().stream()
                .filter(property -> property.isOwnedBy(payerId))
                .forEach(PropertyState::reset);

        economyService.deposit(payer, liquidated);
        economyService.transfer(payer, receiver, payer.getMoney());

        turnService.eliminate(state, payer);
        turnService.passTurn(state);
    }

    private TileData tileAt(int position) {
        return gameDataService.getTiles().get(position);
    }
}