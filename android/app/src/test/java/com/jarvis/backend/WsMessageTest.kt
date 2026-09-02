package com.jarvis.backend

import org.junit.Assert.*
import org.junit.Test

class WsMessageTest {

    @Test
    fun `parse command_response`() {
        val json = """{"type":"command_response","intent":"open_app","response":"Opening WhatsApp","actions":[{"type":"open_app","params":{"package":"com.whatsapp"}}]}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.CommandResponse)
        val resp = msg as WsMessage.CommandResponse
        assertEquals("open_app", resp.intent)
        assertEquals(1, resp.actions.size)
        assertEquals("open_app", resp.actions[0].type)
    }

    @Test
    fun `parse error`() {
        val json = """{"type":"error","message":"Auth rejected"}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.Error)
        assertEquals("Auth rejected", (msg as WsMessage.Error).message)
    }

    @Test
    fun `parse ping`() {
        val json = """{"type":"ping","timestamp":12345}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.Ping)
        assertEquals(12345L, (msg as WsMessage.Ping).timestamp)
    }

    @Test
    fun `parse pong`() {
        val json = """{"type":"pong","timestamp":67890}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.Pong)
    }

    @Test
    fun `parse auth_required`() {
        val json = """{"type":"auth_required","message":"Login"}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.AuthRequired)
    }

    @Test
    fun `parse unknown type returns Unknown`() {
        val json = """{"type":"something_else"}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.Unknown)
    }

    @Test
    fun `parse invalid json returns Unknown`() {
        val msg = WsMessage.parse("not json at all")
        assertTrue(msg is WsMessage.Unknown)
    }

    @Test
    fun `parse action with empty params`() {
        val json = """{"type":"command_response","intent":"go_back","response":"Going back","actions":[{"type":"go_back","params":{}}]}"""
        val msg = WsMessage.parse(json)
        assertTrue(msg is WsMessage.CommandResponse)
        val resp = msg as WsMessage.CommandResponse
        assertEquals("go_back", resp.actions[0].type)
        assertTrue(resp.actions[0].params.isEmpty())
    }
}
