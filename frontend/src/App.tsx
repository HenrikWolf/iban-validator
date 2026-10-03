import { useState } from 'react'
import { apiClient } from './api/client'
import type { components } from './api/schema'

type ValidationResult = components['schemas']['IbanValidationResponse']
type ValidatorType = components['schemas']['IbanValidatorType']

const VALIDATORS: Record<ValidatorType, string> = {
  INTERNAL: 'Internal',
  IBANAPI: 'ibanapi.com',
  IBANAPI_EXTENDED: 'ibanapi.com [erweitert]',
}

function App() {
  const [iban, setIban] = useState('')
  const [validator, setValidator] = useState<ValidatorType>('INTERNAL')
  const [consent, setConsent] = useState(false)
  const [storageConsent, setStorageConsent] = useState(false)
  const [result, setResult] = useState<ValidationResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const needsConsent = validator !== 'INTERNAL'

  function selectValidator(value: ValidatorType) {
    setValidator(value)
    setResult(null)
    setError(null)
    // Consent is given for one choice only; ask again after switching back to the external service.
    setConsent(false)
  }

  async function validate() {
    setResult(null)
    if (iban.trim() === '') {
      setError('Please enter an IBAN.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const { data, response } = await apiClient.POST('/iban/validation', {
        body: { iban, validator },
      })
      setResult(data ?? null)
      if (!data) {
        setError(
          response.status === 503
            ? 'Validation is currently unavailable, please try again later.'
            : 'Validation failed.',
        )
      }
    } catch {
      setError('Validation failed.')
    } finally {
      setLoading(false)
    }
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
            autoComplete="off"
            spellCheck={false}
            value={iban}
            onChange={(event) => setIban(event.target.value)}
          />
          <label htmlFor="validator" className="visually-hidden">
            Validator
          </label>
          <select
            id="validator"
            value={validator}
            onChange={(event) => selectValidator(event.target.value as ValidatorType)}
          >
            {Object.entries(VALIDATORS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={validate}
            disabled={loading || !storageConsent || (needsConsent && !consent)}
          >
            {loading ? 'Validating…' : 'Validate'}
          </button>
        </div>
        <label className="consent">
          <input
            type="checkbox"
            checked={storageConsent}
            onChange={(event) => setStorageConsent(event.target.checked)}
          />
          Ich willige ein, dass meine IBAN zu Testzwecken kurzzeitig
          gespeichert wird.
        </label>
        {needsConsent && (
          <label className="consent">
            <input
              type="checkbox"
              checked={consent}
              onChange={(event) => setConsent(event.target.checked)}
            />
            Ich willige ein, dass meine IBAN zur Validierung an den externen
            Dienst ibanapi.com übertragen wird.
          </label>
        )}
        {result && (
          <p className={result.valid ? 'result valid' : 'result invalid'}>
            {result.valid
              ? result.countryName
                ? `Valid – ${result.countryName} (${result.countryCode})`
                : `Valid (${result.countryCode})`
              : result.failureMessage}
          </p>
        )}
        {result?.valid && (result.bankName || result.bic) && (
          <div className="bank-details">
            {result.bankName && <p>{result.bankName}</p>}
            {result.bic && <p>BIC: {result.bic}</p>}
          </div>
        )}
        {error && <p className="result invalid">{error}</p>}
      </div>
    </main>
  )
}

export default App
