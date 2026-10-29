package com.zendrahub.evolve.core.persist;

import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {

    @TempDir
    Path dir;

    @Test
    void encodeDecodeIsExact() {
        WorldState original = SampleStates.sample();
        String json = StateCodec.toJson(original);
        WorldState copy = StateCodec.fromJson(json);
        assertEquals(json, StateCodec.toJson(copy));
        // spot-check values that are easy to lose: doubles, escapes, ids, causal links
        assertEquals(0.7312345678901234, copy.region(1).env.gravity);
        assertEquals("Skyreach \"East\"", copy.region(1).name);
        assertEquals("stock ran out\nagain", copy.settlement(1).memory.get(0).note());
        assertEquals(-630, copy.building(1).anchorX);
        assertFalse(copy.building(2).hasAnchor);
        assertEquals(5, copy.pendingActions.size());
        assertEquals(6, copy.nextActionId);
        SimEvent outpost = copy.events.all().get(2);
        assertEquals(2, copy.events.causalChain(outpost.id()).size());
        assertEquals(4, copy.events.nextId());
    }

    @Test
    void saveThenLoadRestoresTheWorld() throws IOException {
        Path file = dir.resolve("evolve/state.json");
        WorldState original = SampleStates.sample();
        SaveManager.save(file, original);
        SaveManager.LoadResult loaded = SaveManager.load(file);
        assertFalse(loaded.fromBackup());
        assertTrue(loaded.warnings().isEmpty());
        assertEquals(StateCodec.toJson(original), StateCodec.toJson(loaded.state()));
        assertFalse(Files.exists(file.resolveSibling("state.json.tmp")));
    }

    @Test
    void missingSaveMeansNoWorld() throws IOException {
        SaveManager.LoadResult loaded = SaveManager.load(dir.resolve("nothing.json"));
        assertNull(loaded.state());
        assertFalse(loaded.fromBackup());
    }

    @Test
    void corruptSaveFallsBackToThePreviousOne() throws IOException {
        Path file = dir.resolve("state.json");
        WorldState first = SampleStates.sample();
        SaveManager.save(file, first);
        WorldState second = SampleStates.sample();
        second.day = 99;
        SaveManager.save(file, second);
        assertTrue(Files.exists(SaveManager.backupOf(file)));

        // simulate a crash mid-write: truncate the main file
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, json.substring(0, json.length() / 2), StandardCharsets.UTF_8);

        SaveManager.LoadResult loaded = SaveManager.load(file);
        assertTrue(loaded.fromBackup());
        assertEquals(57, loaded.state().day);
        assertEquals(1, loaded.warnings().size());
    }

    @Test
    void missingMainFileUsesTheBackup() throws IOException {
        Path file = dir.resolve("state.json");
        SaveManager.save(file, SampleStates.sample());
        SaveManager.save(file, SampleStates.sample());
        Files.delete(file);
        SaveManager.LoadResult loaded = SaveManager.load(file);
        assertTrue(loaded.fromBackup());
        assertEquals(57, loaded.state().day);
    }

    @Test
    void bothFilesBrokenIsAnError() throws IOException {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "{ not json");
        Files.writeString(SaveManager.backupOf(file), "[]");
        IOException e = assertThrows(IOException.class, () -> SaveManager.load(file));
        assertTrue(e.getMessage().contains("backup"), e.getMessage());
    }

    @Test
    void newerFormatIsRejectedWithAClearMessage() {
        Map<String, Object> root = StateCodec.encode(SampleStates.sample());
        root.put("formatVersion", 999L);
        SaveFormatException e = assertThrows(SaveFormatException.class, () -> StateCodec.decode(root));
        assertTrue(e.getMessage().contains("newer"), e.getMessage());
    }

    @Test
    void invalidDataNamesTheOffendingField() {
        Map<String, Object> root = StateCodec.encode(SampleStates.sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> cells = (Map<String, Object>) root.get("cells");
        cells.put("timber", List.of(1.0, 2.0));
        SaveFormatException e = assertThrows(SaveFormatException.class, () -> StateCodec.decode(root));
        assertTrue(e.getMessage().contains("$.cells.timber"), e.getMessage());

        Map<String, Object> root2 = StateCodec.encode(SampleStates.sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstTown = ((List<Map<String, Object>>) root2.get("settlements")).get(0);
        firstTown.put("strategy", "CONQUER");
        e = assertThrows(SaveFormatException.class, () -> StateCodec.decode(root2));
        assertTrue(e.getMessage().contains("strategy") && e.getMessage().contains("CONQUER"), e.getMessage());
    }

    @Test
    void danglingReferencesAreRejected() {
        Map<String, Object> root = StateCodec.encode(SampleStates.sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> building = ((List<Map<String, Object>>) root.get("buildings")).get(0);
        building.put("settlementId", 42L);
        SaveFormatException e = assertThrows(SaveFormatException.class, () -> StateCodec.decode(root));
        assertTrue(e.getMessage().contains("settlement 42"), e.getMessage());
    }

    @Test
    void outOfRangeEnvironmentIsClampedOnLoad() {
        Map<String, Object> root = StateCodec.encode(SampleStates.sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) ((List<Map<String, Object>>) root.get("regions")).get(0).get("env");
        env.put("gravity", 50.0);
        WorldState s = StateCodec.decode(root);
        assertEquals(com.zendrahub.evolve.core.model.EnvironmentProfile.GRAVITY_MAX, s.region(0).env.gravity);
    }

    @Test
    void notASaveFile() {
        assertThrows(SaveFormatException.class, () -> StateCodec.fromJson("{\"hello\": 1}"));
        assertThrows(Json.JsonException.class, () -> StateCodec.fromJson("{\"format\": "));
    }
}
