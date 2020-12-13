context('Aliasing', () => {
    beforeEach(() => {
        cy.visit('/')
    })

    it('Verify invalid user', () => {
        cy.get('#email').clear().type("testuser@email.com").should('have.value', 'testuser@email.com')
        cy.get('#passwd').clear().type("mysecret")
        cy.get('#login').click()

        cy.get('#email').should('have.value', '')
        cy.get('#passwd').should('have.value', '')
        cy.contains('incorrect email or password')
    })

    it('Verify create user', () => {
        cy.contains('create account').click()
        cy.contains('Create Account')

        cy.get('#fullname').type("Test User").should('have.value', 'Test User')
        cy.get('#email').type("testuser@email.com")

        // Enter non-matching passwords
        cy.get('#passwd').type("mysecret")
        cy.get('#confirm_passwd').type("mysecret2")
        cy.get('#CreateAccountButton').click()
        cy.contains('Passwords do not match')

        // Correct passwords
        cy.get('#passwd').type("mysecret")
        cy.get('#confirm_passwd').type("mysecret")
        cy.get('#CreateAccountButton').click()
        cy.contains('User testuser@email.com created')
        cy.contains('[log out]').click()

        // Try to create same account again
        cy.contains('create account').click()
        cy.contains('Create Account')
        cy.get('#fullname').type("Test User").should('have.value', 'Test User')
        cy.get('#email').type("testuser@email.com")
        cy.get('#passwd').type("mysecret")
        cy.get('#confirm_passwd').type("mysecret")
        cy.get('#CreateAccountButton').click()
        cy.contains('Email already registered: testuser@email.com')
        cy.contains('Home').click()
    })

})