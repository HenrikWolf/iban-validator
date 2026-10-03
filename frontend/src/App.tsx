import { useState } from 'react'
import { apiClient } from './api/client'
import type { components } from './api/schema'

type ValidationResult = components['schemas']['IbanValidationResponse']

function App() {
  const [iban, setIban] = useState('')
  const [result, setResult] = useState<ValidationResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function validate() {
    const { data, response } = await apiClient.POST('/iban/validation', {
      body: { iban },
    })
    setResult(data ?? null)
    setError(
      data
        ? null
        : response.status === 503
          ? 'Validation is currently unavailable, please try again later.'
          : 'Validation failed.',
    )
  }

  return (
    <main className="page">
      <div>
        <div className="iban-form">
          <label htmlFor="iban" className="visually-hidden">
            IBAN
          </label>
          <input
            id="iban"
            type="text"
            placeholder="DE89 3704 0044 0532 0130 00"
            autoComplete="off"
            spellCheck={false}
            value={iban}
            onChange={(event) => setIban(event.target.value)}
          />
          <button type="button" onClick={validate}>
            Validate
          </button>
        </div>
        {result && (
          <p className={result.valid ? 'result valid' : 'result invalid'}>
            {result.valid
              ? `Valid (${result.countryCode})`
              : result.failureMessage}
          </p>
        )}
        {error && <p className="result invalid">{error}</p>}
      </div>
    </main>
  )
}

export default App
