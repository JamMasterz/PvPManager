package me.NoChance.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import me.NoChance.PvPManager.PvPManager;
import me.NoChance.PvPManager.Config.Messages;
import me.NoChance.PvPManager.Listeners.PlayerListener;
import me.NoChance.PvPManager.Managers.PlayerHandler;
import me.NoChance.PvPManager.Utils.CancelResult;
import me.NoChance.PvPManager.Utils.CombatUtils;

import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ PvPManager.class, CombatUtils.class, PluginCommand.class })
public class DamageListenerTest {

	private static PluginTest pt;
	private static PvPManager plugin;
	private static PlayerListener damageListener;
	private EntityDamageByEntityEvent mockEvent;
	private EntityDamageByEntityEvent projMockEvent;
	private static PlayerHandler ph;
	@Mock(answer = Answers.RETURNS_MOCKS)
	private Player attacker;
	@Mock(answer = Answers.RETURNS_MOCKS)
	private Player defender;

	@BeforeClass
	public static void setupClass() {
		pt = PluginTest.getInstance();
		plugin = pt.getPlugin();
		ph = plugin.getPlayerHandler();
		PowerMockito.mockStatic(CombatUtils.class);
		when(CombatUtils.isWorldAllowed(anyString())).thenReturn(true);
		when(CombatUtils.isPvP((EntityDamageByEntityEvent) Matchers.anyObject())).thenCallRealMethod();
		damageListener = new PlayerListener(plugin);
	}

	@AfterClass
	public static void tearDownClass() {
		pt.tearDown();
	}

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		when(attacker.hasPlayedBefore()).thenReturn(true);
		when(defender.hasPlayedBefore()).thenReturn(true);
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
		when(defender.getUniqueId()).thenReturn(UUID.randomUUID());
		when(attacker.getGameMode()).thenReturn(GameMode.SURVIVAL);
		ph.getPlayers().clear();
		assertTrue(ph.getPlayers().size() == 0);
		ph.get(attacker);
		ph.get(defender);
	}

	@SuppressWarnings("deprecation")
	public final void createAttack(final boolean cancelled) {
		mockEvent = spy(new EntityDamageByEntityEvent(attacker, defender, DamageCause.ENTITY_ATTACK, 5));
		when(mockEvent.isCancelled()).thenReturn(cancelled);

		final Projectile proj = mock(Projectile.class);
		when(proj.getShooter()).thenReturn(attacker);
		projMockEvent = spy(new EntityDamageByEntityEvent(proj, defender, DamageCause.PROJECTILE, 5));
		when(projMockEvent.isCancelled()).thenReturn(cancelled);

		damageListener.onPlayerDamage(mockEvent);
		damageListener.onPlayerDamageOverride(mockEvent);
		damageListener.onPlayerDamageMonitor(mockEvent);
		damageListener.onPlayerDamage(projMockEvent);
		damageListener.onPlayerDamageOverride(projMockEvent);
		damageListener.onPlayerDamageMonitor(projMockEvent);
	}

	@Test
	public final void cancelNewbie() {
		ph.get(attacker).setNewbie(true);
		createAttack(false);

		assertEquals(CancelResult.NEWBIE, ph.tryCancel(attacker, defender));
		verify(attacker, times(2)).sendMessage(Messages.getNewbieProtectionOnHit());

		verify(mockEvent).setCancelled(true);
		verify(projMockEvent).setCancelled(true);
	}

	@Test
	public final void cancelPvPDisabled() {
		ph.get(defender).setPvP(false);
		createAttack(false);

		assertEquals(CancelResult.PVPDISABLED_OTHER, ph.tryCancel(attacker, defender));
		verify(attacker, times(2)).sendMessage(Messages.getAttackDeniedOther().replace("%p", defender.getName()));

		verify(mockEvent).setCancelled(true);
		verify(projMockEvent).setCancelled(true);
	}

	@Test
	public final void failCancel() {
		ph.get(defender).setPvP(true);
		ph.get(attacker).setPvP(true);

		assertEquals(CancelResult.FAIL, ph.tryCancel(attacker, defender));
		createAttack(false);
		verify(attacker, times(1)).sendMessage(Messages.getTaggedAttacker().replace("%p", defender.getName()));
		verify(defender, times(1)).sendMessage(Messages.getTaggedDefender().replace("%p", attacker.getName()));

		verify(mockEvent, never()).setCancelled(true);
		verify(projMockEvent, never()).setCancelled(true);
	}

	@Test
	public final void overrideCancel() {
		ph.get(attacker).toggleOverride();
		createAttack(false);

		assertEquals(CancelResult.FAIL_OVERRIDE, ph.tryCancel(attacker, defender));
		verify(attacker, times(1)).sendMessage(Messages.getTaggedAttacker().replace("%p", defender.getName()));
		verify(defender, times(1)).sendMessage(Messages.getTaggedDefender().replace("%p", attacker.getName()));

		verify(mockEvent, never()).setCancelled(false);
		verify(projMockEvent, never()).setCancelled(false);
	}

	@Test
	public final void overrideCancelled() {
		ph.get(attacker).toggleOverride();
		createAttack(true);

		assertEquals(CancelResult.FAIL_OVERRIDE, ph.tryCancel(attacker, defender));
		verify(attacker, times(1)).sendMessage(Messages.getTaggedAttacker().replace("%p", defender.getName()));
		verify(defender, times(1)).sendMessage(Messages.getTaggedDefender().replace("%p", attacker.getName()));

		verify(mockEvent).setCancelled(false);
		verify(projMockEvent).setCancelled(false);
	}

}
