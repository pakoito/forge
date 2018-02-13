package forge.ai.ability;

import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilMana;
import forge.ai.SpellAbilityAi;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;

import java.util.List;
import java.util.Random;

public class DigUntilAi extends SpellAbilityAi {

    @Override
    protected boolean canPlayAI(Player ai, SpellAbility sa) {
        Card source = sa.getHostCard();
        final String logic = sa.getParamOrDefault("AILogic", "");
        double chance = .4; // 40 percent chance with instant speed stuff
        if (SpellAbilityAi.isSorcerySpeed(sa)) {
            chance = .667; // 66.7% chance for sorcery speed (since it will
                           // never activate EOT)
        }
        // if we don't use anything now, we wasted our opportunity.
        if ((ai.getGame().getPhaseHandler().is(PhaseType.END_OF_TURN))
                && (!ai.getGame().getPhaseHandler().isPlayerTurn(ai))) {
            chance = 1;
        }

        final Random r = MyRandom.getRandom();
        final boolean randomReturn = r.nextFloat() <= Math.pow(chance, sa.getActivationsThisTurn() + 1);

        Player libraryOwner = ai;
        Player opp = ComputerUtil.getOpponentFor(ai);

        if ("DontMillSelf".equals(logic)) {
            // A card that digs for specific things and puts everything revealed before it into graveyard
            // (e.g. Hermit Druid) - don't use it to mill itself and also make sure there's enough playable
            // material in the library after using it several times.
            // TODO: maybe this should happen for any DigUntil SA with RevealedDestination$ Graveyard?
            if (ai.getCardsIn(ZoneType.Library).size() < 20) {
                return false;
            }
            if ("Land.Basic".equals(sa.getParam("Valid"))
                    && !CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.Presets.LANDS_PRODUCING_MANA).isEmpty()) {
                // We already have a mana-producing land in hand, so bail
                // until opponent's end of turn phase!
                // But we still want more (and want to fill grave) if nothing better to do then
                // This is important for Replenish/Living Death type decks
                if (!((ai.getGame().getPhaseHandler().is(PhaseType.END_OF_TURN))
                        && (!ai.getGame().getPhaseHandler().isPlayerTurn(ai)))) {
                    return false;
                }
            }
        }

        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (!sa.canTarget(opp)) {
                return false;
            } else {
                sa.getTargets().add(opp);
            }
            libraryOwner = opp;
        } else {
            if (sa.hasParam("Valid")) {
                final String valid = sa.getParam("Valid");
                if (CardLists.getValidCards(ai.getCardsIn(ZoneType.Library), valid.split(","), source.getController(), source, sa).isEmpty()) {
                    return false;
                }
            }
        }

        final String num = sa.getParam("Amount");
        if ((num != null) && num.equals("X") && source.getSVar(num).equals("Count$xPaid")) {
            // Set PayX here to maximum value.
            if (!(sa instanceof AbilitySub) || source.getSVar("PayX").equals("")) {
                int numCards = ComputerUtilMana.determineLeftoverMana(sa, ai);
                if (numCards <= 0) {
                    return false;
                }
                source.setSVar("PayX", Integer.toString(numCards));
            }
        }

        // return false if nothing to dig into
        if (libraryOwner.getCardsIn(ZoneType.Library).isEmpty()) {
            return false;
        }

        return randomReturn;
    }

    @Override
    protected boolean doTriggerAINoCost(Player ai, SpellAbility sa, boolean mandatory) {

        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (sa.isCurse()) {
                for (Player opp : ai.getOpponents()) {
                    if (sa.canTarget(opp)) {
                        sa.getTargets().add(opp);
                        break;
                    }
                }
                if (mandatory && sa.getTargets().isEmpty() && sa.canTarget(ai)) {
                    sa.getTargets().add(ai);
                }
            } else {
                if (sa.canTarget(ai)) {
                    sa.getTargets().add(ai);
                }
            }
        }

        return true;
    }

    /* (non-Javadoc)
     * @see forge.card.ability.SpellAbilityAi#confirmAction(forge.card.spellability.SpellAbility, forge.game.player.PlayerActionConfirmMode, java.lang.String)
     */
    @Override
    public boolean confirmAction(Player player, SpellAbility sa, PlayerActionConfirmMode mode, String message) {
        if (sa.hasParam("AILogic")) {
            final String logic = sa.getParam("AILogic");
            if ("OathOfDruids".equals(logic)) {
                final List<Card> creaturesInLibrary =
                        CardLists.filter(player.getCardsIn(ZoneType.Library), CardPredicates.Presets.CREATURES);
                final List<Card> creaturesInBattlefield =
                        CardLists.filter(player.getCardsIn(ZoneType.Battlefield), CardPredicates.Presets.CREATURES);
                // if there are at least 3 creatures in library,
                // or none in play with one in library, oath
                return creaturesInLibrary.size() > 2
                        || (creaturesInBattlefield.size() == 0 && creaturesInLibrary.size() > 0);

            }
        }
        return true;
    }
}
