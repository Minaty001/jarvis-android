import { z } from "zod";

export const OpenAppAction = z.object({
  type: z.literal("open_app"),
  params: z.object({
    package: z.string().min(1)
  })
});

export const TapAction = z.object({
  type: z.literal("tap"),
  params: z.object({
    text: z.string().min(1)
  })
});

export const TypeAction = z.object({
  type: z.literal("type"),
  params: z.object({
    text: z.string().min(1).max(2000)
  })
});

export const SwipeAction = z.object({
  type: z.literal("swipe"),
  params: z.object({
    direction: z.enum(["up", "down", "left", "right"]).optional()
  })
});

export const PressBackAction = z.object({
  type: z.literal("press_back"),
  params: z.object({})
});

export const ReadScreenAction = z.object({
  type: z.literal("read_screen"),
  params: z.object({})
});

export const SendSmsAction = z.object({
  type: z.literal("send_sms"),
  params: z.object({
    phone: z.string().min(3),
    message: z.string().min(1).max(5000)
  })
});

export const MakeCallAction = z.object({
  type: z.literal("make_call"),
  params: z.object({
    phone: z.string().min(3)
  })
});

export const OpenUrlAction = z.object({
  type: z.literal("open_url"),
  params: z.object({
    url: z.string().url()
  })
});

export const MediaControlAction = z.object({
  type: z.literal("media_control"),
  params: z.object({
    action: z.enum(["play", "pause", "next", "previous", "volume_up", "volume_down"])
  })
});

export const WifiAction = z.object({
  type: z.literal("wifi"),
  params: z.object({
    action: z.enum(["on", "off", "toggle"])
  })
});

export const BluetoothAction = z.object({
  type: z.literal("bluetooth"),
  params: z.object({
    action: z.enum(["on", "off", "toggle"])
  })
});

export const CalendarAction = z.object({
  type: z.literal("calendar"),
  params: z.object({
    action: z.enum(["today", "search"]),
    query: z.string().optional()
  })
});

export const ShareAction = z.object({
  type: z.literal("share"),
  params: z.object({
    text: z.string().min(1).max(5000)
  })
});

export const ActionSchema = z.discriminatedUnion("type", [
  OpenAppAction,
  TapAction,
  TypeAction,
  SwipeAction,
  PressBackAction,
  ReadScreenAction,
  SendSmsAction,
  MakeCallAction,
  OpenUrlAction,
  MediaControlAction,
  WifiAction,
  BluetoothAction,
  CalendarAction,
  ShareAction
]);

export const ActionPlanSchema = z.object({
  actions: z.array(ActionSchema).min(1).max(10)
});