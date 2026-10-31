import { expect, test, type Locator, type Page } from '@playwright/test'

/**
 * Happy paths through the real UI and backend. The game API behind the backend is the stub in
 * backend/src/test/java/com/ponomarenko/mugloar/e2e/StubMugloarApi.java: every game starts with
 * 3 lives and no gold, the board never changes, "Escort a merchant caravan" (60 gold, Sure thing)
 * is the best honest ad, the "super awesome diamond" ad is a trap, and a healing potion costs 50.
 */

/** The value of a stat in the status bar, by its label. */
function stat(page: Page, label: string): Locator {
  return page
    .locator('.stat')
    .filter({ has: page.locator('.stat__label', { hasText: label }) })
    .locator('.stat__value')
}

/** The newest-first entries of the history log. */
function history(page: Page): Locator {
  return page.locator('.history__item')
}

async function startGame(page: Page): Promise<string> {
  await page.goto('/')
  await page.getByRole('button', { name: 'Start a new game' }).click()
  await expect(page).toHaveURL(/\/games\/e2e-\d+$/)
  return page.url().replace(/.*\/games\//, '')
}

test('starts a game, shows the board, shop and advisor, and resumes it from the start page', async ({
  page,
}) => {
  const id = await startGame(page)
  await expect(page).toHaveTitle(`Game ${id} · Dragons of Mugloar`)

  await expect(stat(page, 'Lives').locator('.stat__number')).toHaveText('3')
  await expect(stat(page, 'Gold')).toHaveText('0')
  await expect(stat(page, 'Score')).toHaveText('0')
  await expect(stat(page, 'Turn')).toHaveText('0')

  const ads = page.getByRole('article')
  await expect(ads).toHaveCount(5)
  const recommended = ads.filter({ hasText: 'Recommended' })
  await expect(recommended).toHaveCount(1)
  await expect(recommended).toContainText('Escort a merchant caravan')
  const trap = ads.filter({ hasText: 'super awesome diamond' })
  await expect(trap).toHaveClass(/is-ineligible/)
  await expect(trap).toContainText('Known trap')

  // Nothing is affordable yet, so no button can waste a turn on a failed purchase.
  await expect(page.getByRole('button', { name: 'Buy Healing potion' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Play one turn' })).toBeEnabled()
  await expect(page.getByText('Nothing has happened yet')).toBeVisible()

  await page.goto('/')
  const listed = page.locator('.recent__item').filter({ has: page.getByText(id, { exact: true }) })
  await expect(listed).toContainText('in progress')
  await page.getByRole('link', { name: 'Resume last game' }).click()
  await expect(page).toHaveURL(new RegExp(`/games/${id}$`))
  await expect(stat(page, 'Game')).toHaveText(id)
})

test('solves the recommended ad and buys a potion with the reward', async ({ page }) => {
  await startGame(page)

  await page
    .getByRole('article')
    .filter({ hasText: 'Recommended' })
    .getByRole('button', { name: 'Solve' })
    .click()

  await expect(page.getByRole('status').filter({ hasText: 'Solved' })).toBeVisible()
  await expect(stat(page, 'Gold')).toHaveText('60')
  await expect(stat(page, 'Score')).toHaveText('60')
  await expect(stat(page, 'Turn')).toHaveText('1')
  await expect(history(page)).toHaveCount(1)
  await expect(history(page).first()).toContainText('#1')
  await expect(history(page).first()).toContainText('solved')
  await expect(history(page).first()).toContainText('Escort a merchant caravan')
  await expect(page.getByText('1 actions · 1 solved · 0 failed')).toBeVisible()

  // The reward makes the potion affordable; buying it costs a turn and adds a life.
  await page.getByRole('button', { name: 'Buy Healing potion' }).click()

  await expect(page.getByRole('status').filter({ hasText: 'Bought' })).toBeVisible()
  await expect(stat(page, 'Lives').locator('.stat__number')).toHaveText('4')
  await expect(stat(page, 'Gold')).toHaveText('10')
  await expect(stat(page, 'Turn')).toHaveText('2')
  await expect(history(page)).toHaveCount(2)
  await expect(history(page).first()).toContainText('#2')
  await expect(history(page).first()).toContainText('bought')
  await expect(history(page).first()).toContainText('Healing potion')
})

test('lets the advisor play one turn and then finish the game on the server', async ({ page }) => {
  await startGame(page)

  await page.getByRole('button', { name: 'Play one turn' }).click()

  await expect(stat(page, 'Turn')).toHaveText('1')
  await expect(history(page)).toHaveCount(1)
  await expect(history(page).first()).toContainText('advisor')
  await expect(history(page).first()).toContainText('Why:')
  await expect(page.getByText('1 turn played by the advisor')).toBeVisible()

  // The e2e backend caps a server-side run at 15 turns (mugloar.strategy.max-turns), so the run
  // stops for that reason rather than at game over, and the game can go on afterwards.
  await page.getByRole('button', { name: 'Finish game' }).click()

  const notice = page.getByRole('status').filter({ hasText: 'Reached the turn limit of 15' })
  await expect(notice).toBeVisible({ timeout: 30_000 })
  await expect(stat(page, 'Turn')).toHaveText('16')
  await expect(history(page)).toHaveCount(16)
  await expect(page.getByText('16 turns played by the advisor')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Finish game' })).toBeEnabled()
  await expect(page.getByRole('button', { name: 'Play one turn' })).toBeEnabled()
})
