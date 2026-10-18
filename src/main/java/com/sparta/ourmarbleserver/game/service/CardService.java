package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.CardData;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.OptionalInt;

/**
 * 황금열쇠 카드 효과를 적용하고 다음 흐름(턴 종료 또는 도착 칸 처리)까지 진행한다. (클라 ExecuteCardEffect)
 * 상태만 바꾸고, 요청 검증, 카드 고르기, 저장, 알림, 요청 락은 GameService가 한다.
 * 같은 패키지에서만 보이게 해서 GameService를 거치지 않고는 부를 수 없다. (요청 락을 우회하지 못하게)
 */
@Service
@RequiredArgsConstructor
public class CardService {
    private final TurnService turnService;
    private final MoveService moveService;
    private final EconomyService economyService;
    private final ArrivalService arrivalService;

    /** 카드 효과를 적용하고 다음 흐름(턴 종료 또는 도착 칸 처리)까지 진행한다. (클라 ExecuteCardEffect) */
    void applyCardEffect(GameState state, PlayerState player, CardData card) {
        switch (card.effectType()) {
            case "Bonus" -> {
                economyService.deposit(player, card.amount());
                turnService.endTurn(state);
            }
            case "Penalty" -> {
                economyService.payPenalty(state, player, card.amount(), card.penaltyToFestivalPool());
                turnService.endTurn(state);
            }
            case "MoveTo" -> {
                MoveService.MoveResult move = moveService.moveTo(player, card.targetTileId());
                if (move.passedStart()) {
                    economyService.paySalary(player, state.getConfig().salaryAmount());
                }
                arrivalService.processArrival(state, player);
            }
            case "MoveBy" -> moveByCard(state, player, card.steps());
            case "GoToInspection" -> goToIsland(state, player);
            default -> turnService.endTurn(state); // 알 수 없는 효과는 턴이 멈추지 않도록 종료한다.
        }
    }

    /** N칸 이동 카드. 양수면 앞으로(출발 지점을 지나면 월급), 음수면 뒤로(월급 없음). */
    private void moveByCard(GameState state, PlayerState player, int steps) {
        if (steps > 0) {
            MoveService.MoveResult move = moveService.moveBy(player, steps);
            if (move.passedStart()) {
                economyService.paySalary(player, state.getConfig().salaryAmount());
            }
        } else {
            int size = moveService.getTileCount();
            moveService.moveDirectly(player, ((player.getPosition() + steps) % size + size) % size);
        }
        arrivalService.processArrival(state, player);
    }

    /** 무인도 카드. 월급 없이 무인도로 가고, 도착 처리에서 영업정지가 시작되고 턴이 넘어간다. */
    private void goToIsland(GameState state, PlayerState player) {
        OptionalInt island = turnService.findIslandPosition();
        if (island.isEmpty()) {
            turnService.endTurn(state);
            return;
        }
        moveService.moveDirectly(player, island.getAsInt());
        arrivalService.processArrival(state, player);
    }
}
