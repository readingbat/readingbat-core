context('Aliasing', () => {
    beforeEach(() => {
        cy.visit('/')
    })

    it('Verify homepage redirect', () => {
        cy.url().should('include', '/content/java')
    })

    it('Verify homepage basics', () => {
        cy.contains('ReadingBat')
        cy.contains('code reading practice')
        cy.contains('Welcome to ReadingBat.')
    })

    it('Verify about link', () => {
        cy.contains('about').click()
        cy.url().should('include', '/about?')
        cy.contains('About ReadingBat')
    })

    it('Verify help link', () => {
        cy.contains('help').click()
        cy.url().should('include', '/help?')
        cy.contains('ReadingBat Help')
    })

    it('Verify anonymous prefs link', () => {
        cy.contains('prefs').click()
        cy.url().should('include', '/user-prefs?')
        cy.contains('Log in')
        cy.contains('Please create an account')

        cy.contains('Privacy Statement').click()
        cy.url().should('include', '/privacy?')
        cy.contains('ReadingBat Privacy')
        cy.contains('Back').click()
        cy.url().should('include', '/user-prefs')

        cy.contains('Home').click()
        cy.url().should('include', '/content/java')
    })


})