context('Aliasing', () => {
    beforeEach(() => {
        cy.visit('/')
    })

    it('Verify about link', () => {
        cy.contains('Java').click()
        cy.url().should('include', '/content/java')

        cy.contains('Boolean Expressions').click()
        cy.url().should('include', '/content/java/Boolean%20Expressions')

        cy.contains('LessThan').click()
        cy.url().should('include', 'content/java/Boolean%20Expressions/LessThan')

        cy.tryValue(0, 'False', 'rgb(255, 0, 0)')
        cy.tryValue(1, 'False', 'rgb(255, 0, 0)')
        cy.tryValue(2, 'False', 'rgb(255, 0, 0)')
        cy.tryValue(3, 'False', 'rgb(255, 0, 0)')
        cy.tryValue(4, 'False', 'rgb(255, 0, 0)')

        cy.screenshot('clicking-on-nav')

        cy.tryValue(0, 'true', 'rgb(78, 170, 58)')
        cy.tryValue(1, 'true', 'rgb(78, 170, 58)')
        cy.tryValue(2, 'false', 'rgb(78, 170, 58)')
        cy.tryValue(3, 'false', 'rgb(78, 170, 58)')
        cy.tryValue(4, 'true', 'rgb(78, 170, 58)')
    })
})