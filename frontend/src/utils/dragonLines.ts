/**
 * Shown one at a time while the server plays a game to the end. Nothing comes back until the
 * run is over, and a good run can take minutes, so the player gets some company meanwhile.
 */
export const DRAGON_LINES: readonly string[] = [
  'Dragons do not hoard gold out of greed. The shop has no return policy.',
  "A dragon's favourite label is Sure thing. Its least favourite is the fine print.",
  'The advisor has never lost a dragon. It has misplaced a few.',
  'Escorting people to grassland pays well. Nobody asks what the dragon does after lunch.',
  'Claw sharpening costs 100 gold. Claw dulling is free and comes with every failed ad.',
  'A healing potion adds a life and costs a turn. The dragon spends that turn sulking.',
  'The super awesome diamond was never there. The dragon checked nine times.',
  'A level 300 dragon fears neither the state nor the underworld. It fears HTTP 429.',
  "If this takes a while, the game's rate limit has kicked in and the dragon is practising smoke rings.",
  'Every solved ad is a story. Every failed ad is a very short one.',
]

/** How long each line stays on screen. */
export const LINE_INTERVAL_MS = 4000
