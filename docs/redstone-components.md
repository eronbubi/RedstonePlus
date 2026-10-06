# RedstonePlus components: how they really work

Read from eronbubi's code (`block/`, `block/gate/`, `redstone/`, `item/ModItems.java`) on 2026-10-05.
This is the reference for building working machines in the Redstone Realm. When in doubt, read the class named
in each row: this file must match the code, not the other way round.

Times are in **game ticks** (20 per second). "rt" means a redstone tick = 2 game ticks.

## 1. Logic gates (`gate/GateBlock`, `gate/Gates`)

All gates are flat (2 px) repeater-like blocks.

- **Facing:** `FACING` (horizontal) is the **output** direction. The **back** is `FACING.opposite`, and **left/right** are
  `FACING.counterClockWise` and `FACING.clockWise`.
- **Inputs:** read with `GateBlock.input(level, pos, side)`, the max of `getSignal` and the redstone wire `POWER`
  next to it. Any signal above 0 counts as "on".
- **When they update:** every state change runs in a scheduled tick (`reactionDelay`, 2 game ticks unless stated
  otherwise), never inside a neighbour update.
- **How they output:** after a change, `notifyOutputs` wakes all 4 horizontal neighbours *and the blocks around
  them*, so strong power through a block works.
- **Output strength:** 15 towards `FACING` while `POWERED`, unless the row says otherwise. Gates are signal sources
  that connect to wire on horizontal sides only.
- **Placing by hand:** facing = the way the player looks. Right click cycles `DELAY` 1-20 on gates that have it
  (sneak cycles down).

| Block | Reads | Output | Timing / state |
|---|---|---|---|
| AND, OR, XOR, NAND, NOR, XNOR (`LogicGate`) | left, right | front 15 | 2 gt |
| NOT, AMPLIFIER (`LogicGate`) | **back** | front 15: NOT = !back, AMPLIFIER = back | 2 gt |
| `t_flip_flop` (`FlipFlop`) | back, rising edge | front, toggles | `INPUT` remembers the last back value |
| `rs_latch` | left = set, right = reset (reset wins) | front | holds |
| `pulse_limiter` | back, rising edge | front, 2 gt pulse | |
| `pulse_extender` | back | front, stays on `DELAY` **seconds** after the input falls | default `DELAY` 2 |
| `delay_block` (`Delay`) | back | front = back after `DELAY`×2 gt | default `DELAY` 4 |
| `randomizer` | back, rising edge | front, random on/off | |
| `counter` | back, rising edges; left or right resets | front, **analog = count 0-15** (wraps 15→0) | `COUNT`, `POWERED` = count > 0 |
| `sequencer` | back, rising edge | 15 to **one** side: STEP 0 = left, 1 = front, 2 = right | Each edge advances STEP *first*, so the first pulse lights **front**. Output only while back is on: `POWERED` = back. |
| `redstone_crossing` | back → front; **left → right** | two independent lines | `SIDE_POWERED` |
| `clock` | back = **pause** (output off while back is on) | front toggles every `DELAY`×2 gt | default `DELAY` 10 (1 s on, 1 s off). It schedules itself from its own tick, so it needs one scheduled tick to start. |
| `edge_detector` | back, either edge | front, 2 gt pulse | |
| `analog_inverter` | back | front = 15 − back | uses `Counter.COUNT` as its output |
| `signal_adder` | left + right | front, sum clamped to 15 | |
| `signal_subtractor` | back − max(left, right) | front, clamped to 0 | |
| `nuclear_repeater` | back | front 15. Also keeps up to 200 connected redstone wires at full 15 (`NuclearNetwork`, retraced every 20 gt) | |

**Sequencer pattern** (the realm uses it a lot): clock → sequencer, with lamps on its left, front and right. The
lamps light **front, right, left, front...** (one step per clock rising edge), and each only while the clock is on.

## 2. Sensors (`Sensors.Sensor`)

- **Output:** the same `POWER` (0-15) on **all sides**, as a weak signal (`getSignal`).
- **Measuring:** each sensor measures in a self-scheduled tick, every `interval()` ticks (10 by default, 2 for the
  laser sensor and the block detector).
- **Starting:** sensors start only when `onPlace` schedules their first tick. In worldgen, schedule it yourself.
- **Updating neighbours:** when the value changes, `updateNeighborsAt`, so lamps and wire next to the sensor react.

| Block | Measures |
|---|---|
| `player_detector` | 15 if a non-spectator player is within 8 blocks of the centre (creative players count) |
| `mob_detector` | `Enemy` mobs within 8: 0, or `min(15, 7 + count)` |
| `entity_counter` | number of living non-spectator entities within 8 (players included), up to 15 |
| `item_detector` | sum of dropped item stack sizes within 5 |
| `weather_sensor` | 0 clear, 7 rain, 15 thunder |
| `night_sensor` | 15 at night (`isNight`) |
| `light_sensor` | raw brightness of the block **above** (0-15) |
| `laser_sensor` | `FACING` (6 directions, the beam goes out of the front): 15 while a living entity or item is in the beam (up to 32 blocks, stopped by solid blocks) |
| `block_detector` | `FACING`: 15 while the block in front is not air |

## 3. Powered machines (`PoweredBlock`, `MachineBlock`)

- **Power input:** `hasNeighborSignal` from **any side**, like a dispenser. `POWERED` mirrors it, but is only
  updated in `neighborChanged`.
- **When they fire:**
  - **Edge-triggered** (`workInterval` 0): a rising edge schedules a tick 1 gt later, and the tick calls `onRise`.
    The tick does **not** re-check power, so any scheduled tick fires the machine.
  - **Continuous** (`workInterval` > 0): `work` every N ticks while `POWERED`.
  - `reactsToFall`: the tick calls `onFall` when unpowered (only the Fire Starter uses this).
- **Facing:** `MachineBlock.FACING` is one of 6 directions and is the front. Machines rotate correctly
  (`MachineBlock.rotate`).

| Block | Kind | Effect |
|---|---|---|
| `tnter` / `freeze_tnter` | edge | primed TNT / frozen TNT in front (the frozen kind becomes live when it passes an `activator_net`) |
| `block_breaker` | edge | destroys the block in front (with drops, not bedrock) |
| `block_placer` | edge | places a block from its 3×3 inventory in front (block entity) |
| `arrow_shooter` | edge | fires an arrow salvo from its inventory (block entity with a ticker) |
| `lightning_caller` | edge | lightning at the first solid block up to 64 in front (or at 64) |
| `tnt_cannon` | edge | TNT thrown in an arc, fuse 60 |
| `fireball_launcher` | edge | ghast fireball straight ahead |
| `crop_harvester` | edge | harvests and replants ripe crops in a 5×5×3 area centred 3 in front |
| `fire_starter` | edge + fall | fire in front on the rise, put out on the fall |
| `fan` | continuous 1 | pushes entities in the 8 blocks in front |
| `ice_maker` | edge | freezes water sources in a 5×5×5 area centred 3 in front |
| `water_pump` | edge | removes liquid blocks in a 5×5×5 area centred 3 in front |
| `firework_launcher` | edge | random rocket |
| `snowball_turret` | edge | 8 snowballs, spread out |
| `anvil_dropper` | edge | anvil falls from 12 above the spot 6 in front |
| `cluster_tnter` | edge | 5 TNT in a ring |
| `tnt_rain` | edge | 9 TNT falling from 20 above the spot 5 in front |
| `block_swapper` | edge | swaps the blocks in front and behind (no block entities) |
| `launch_pad` | edge | throws entities standing on it up (2.4) |
| `heal_pad` / `speed_pad` | continuous 20 / 5 | heals 2 / speed IV for whoever stands on it |
| `smoke_emitter` | continuous 3 | campfire smoke |
| `item_magnet` | continuous 1 | pulls items within 10; feeds a container directly below |
| `alarm_siren` | continuous 20 | bell + pling, loud |
| `anti_gravity_field` | continuous 1 | levitation 4 sideways, 12 up |
| `spike_block` | continuous 10 | 4 damage to whoever stands on it (not creative) |

## 4. Other blocks

- **`instant_lamp` / `inverted_lamp`:**
  - `LIT` follows `hasNeighborSignal` with no delay. The inverted lamp is lit while it is **not** powered.
  - `LIT` changes only in `neighborChanged`. In worldgen, set `LIT` by hand to match what powers it.
- **`phantom_block`:** passable and see-through while powered.
- **`conveyor_belt`:**
  - `FACING` horizontal. Moves entities standing on it that way; stops while powered.
  - Sneaking entities aren't moved.
- **`signal_display`:** shows the strongest neighbour signal as a number (`POWER`).
- **`variable_source`:** constant `POWER` 1-15 on all sides (right click to change it).
- **`reinforced_redstone_block`, `uranium_block`:** constant 15 on all sides.
- **`landmine`:** explodes (3.5) when a living entity that isn't sneaking steps on it, or when powered.
- **`uranium_nuke`:** primes a huge nuke when powered or lit. **Never use it in worldgen.**
- **`activator_net`:** turns frozen TNT passing through into live TNT.
- **`entity_teleporter`:** collects entities standing on it (block entity). The comparator reads the stored count.
- **`wireless_transmitter` / `wireless_receiver`:**
  - 16 channels shared by the **whole server**, across every dimension.
  - A powered transmitter keeps its channel active, and every receiver on that channel outputs 15.
  - The `redstone_remote` sends a 1 s pulse.
- **`super_piston` / `sticky_super_piston`:**
  - Pushes up to `PUSH_LIMIT` blocks, with an arm 1 to `MAX_RANGE` long (slider in its menu).
  - It moves one block every 4 gt.
  - It is powered like a vanilla piston: any side except the front, plus the block above.

## 5. Building them in worldgen: rules

These are all things that went wrong at least once.

1. **Rotation:**
   - `GateBlock` and the sensors do **not** override `rotate()`. `state.rotate(r)` leaves their `FACING` unchanged.
   - `RealmFeatures.Build.set` therefore rotates `HORIZONTAL_FACING` / `FACING` itself when `rotate()` changed
     nothing (fixed 2026-10-05).
   - Before that fix, every gate in a rotated structure pointed the wrong way, which was three out of four
     generated machines.
2. **Starting tick-driven blocks:** worldgen places blocks without `onPlace`, so tick-driven blocks never start on
   their own.
   - `Build.set` schedules a tick for gates, sensors and wireless receivers.
   - **Do not schedule ticks for edge-triggered `PoweredBlock`s**: the tick fires them (`onRise`) even unpowered.
   - Drive them from a gate instead. The gate's `notifyOutputs` sends them a neighbour update when it changes.
3. **Constant sources** (redstone block, reinforced redstone block, variable source) never send updates in
   worldgen. Anything they power must be placed in its powered state (`LIT=true` lamp, `POWERED=true` phantom
   block), or be a gate, which reads its inputs on its scheduled tick.
4. **Redstone wire** placed in worldgen keeps the `POWER` it was placed with until a neighbour update reaches it.
   - Gates feeding wire are fine: `notifyOutputs` updates it.
   - Avoid wire that is driven *only* by constant sources.
5. **Wireless** is global (rule 4 in section 4). Generated structures must not use it, or every copy triggers every
   other copy.
6. **No world-destroying machines on timers:** no TNTer, cluster TNTer, TNT rain, nuke, cannon or fireball
   launcher, or block breaker / swapper aimed at terrain, on clocks or player detectors. They are fine behind a
   player's lever or as one-shot traps in a sealed room.
7. **Containers:** set loot with `RandomizableContainer.setBlockEntityLootTable` (works in worldgen). Block placers
   and arrow shooters need their inventory filled through the block entity after placement.
8. **Place sources before what they power.** In a live world (game tests, structures built at runtime) `onPlace`
   runs for every placed block, so a machine placed before its redstone block reads "unpowered" and switches off.
   In worldgen nothing runs, so the order only shows up in tests and runtime builds.
9. Features may only write into the chunks next to their origin (`Build.set` drops the rest), so keep a machine
   within about 20 blocks of its origin.

## 6. Testing

`runGameTestServer` runs every `@GameTest` in the mod.

- Keep test classes out of git: list them in `.git/info/exclude` and keep a copy as `*.keep` in the scratchpad.
- **Move `run/mods/*.jar` (BlueMap, Chunky) out first.** BlueMap crashes the game-test server.
- Force-load the chunks you build in (`level.setChunkForced`), or nothing ticks.
- Build with `RealmFeatures.Build(level, origin, rotation, random, true)` for **each of the 4 rotations**, then watch
  the blocks that should change state (`LIT`, `POWERED`, `COUNT`, entities) in `helper.onEachTick`.
- The realm dimension does not exist on the game-test server, so code that checks `Realm.REALM` cannot be tested
  there.
