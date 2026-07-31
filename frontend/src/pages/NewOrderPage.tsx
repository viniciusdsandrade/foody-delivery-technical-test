import { useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../hooks/useToast';
import { errorMessage } from '../services/apiError';
import { createOrder } from '../services/orderService';
import type { ApiError } from '../types';
import { formatCurrency } from '../utils/format';

interface ItemForm {
  id: number;
  name: string;
  quantity: string;
  unitPrice: string;
}

function makeItem(id: number): ItemForm {
  return { id, name: '', quantity: '1', unitPrice: '' };
}

const inputClass =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-orange-500 focus:ring-2 focus:ring-orange-200';
const labelClass = 'mb-1 block text-sm font-medium text-slate-700';

function parsePrice(raw: string): number {
  const trimmed = raw.trim();
  // Com vírgula decimal (pt-BR), pontos são separador de milhar: "1.500,00".
  // Sem vírgula, o ponto é decimal: "10.50".
  if (trimmed.includes(',')) {
    return Number(trimmed.replace(/\./g, '').replace(',', '.'));
  }
  // "1.500" sem vírgula: grupos de exatamente 3 dígitos são milhar, não decimal.
  if (/^\d{1,3}(\.\d{3})+$/.test(trimmed)) {
    return Number(trimmed.replace(/\./g, ''));
  }
  return Number(trimmed);
}

function describeError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.data) {
    const data = err.response.data as ApiError;
    if (data.fieldErrors?.length) {
      return 'O servidor recusou alguns campos. Revise nome, itens (quantidade e preço) e endereço.';
    }
  }
  return errorMessage(err, 'Não foi possível criar o pedido. Tente novamente.');
}

export default function NewOrderPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [customerName, setCustomerName] = useState('');
  const [address, setAddress] = useState({
    street: '',
    number: '',
    complement: '',
    district: '',
    city: '',
    state: '',
    zipCode: '',
  });
  const [items, setItems] = useState<ItemForm[]>([makeItem(0)]);
  const nextItemId = useRef(1);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const total = useMemo(
    () =>
      items.reduce((sum, item) => {
        const quantity = Number(item.quantity);
        const unitPrice = parsePrice(item.unitPrice);
        return sum + (Number.isFinite(quantity) && Number.isFinite(unitPrice) ? quantity * unitPrice : 0);
      }, 0),
    [items],
  );

  function updateAddress(field: keyof typeof address, value: string) {
    setAddress((current) => ({ ...current, [field]: value }));
  }

  function updateItem(index: number, patch: Partial<ItemForm>) {
    setItems((current) => current.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  }

  function addItem() {
    const id = nextItemId.current;
    nextItemId.current += 1;
    setItems((current) => [...current, makeItem(id)]);
  }

  function removeItem(index: number) {
    setItems((current) => current.filter((_, i) => i !== index));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    const invalidItem = items.some(
      (item) => !item.name.trim() || !(Number(item.quantity) >= 1) || !(parsePrice(item.unitPrice) > 0),
    );
    if (invalidItem) {
      setError('Revise os itens: nome obrigatório, quantidade maior ou igual a 1 e preço maior que zero.');
      return;
    }

    setSubmitting(true);
    try {
      const order = await createOrder({
        customerName: customerName.trim(),
        address: {
          street: address.street.trim(),
          number: address.number.trim(),
          complement: address.complement.trim() || null,
          district: address.district.trim(),
          city: address.city.trim(),
          state: address.state.trim(),
          zipCode: address.zipCode.trim(),
        },
        items: items.map((item) => ({
          name: item.name.trim(),
          quantity: Number(item.quantity),
          unitPrice: parsePrice(item.unitPrice),
        })),
      });
      toast.success(`Pedido #${order.id} criado com sucesso!`);
      navigate(`/orders/${order.id}`);
    } catch (err) {
      toast.error(describeError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Novo pedido</h1>
        <Link to="/orders" className="text-sm font-medium text-slate-500 hover:text-slate-800">
          Voltar
        </Link>
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <section className="rounded-xl bg-white p-5 shadow-sm">
          <h2 className="mb-4 font-semibold text-slate-800">Cliente</h2>
          <label htmlFor="customerName" className={labelClass}>
            Nome do cliente
          </label>
          <input
            id="customerName"
            type="text"
            required
            maxLength={120}
            value={customerName}
            onChange={(event) => setCustomerName(event.target.value)}
            className={inputClass}
          />
        </section>

        <section className="rounded-xl bg-white p-5 shadow-sm">
          <h2 className="mb-4 font-semibold text-slate-800">Endereço de entrega</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label htmlFor="street" className={labelClass}>Rua</label>
              <input id="street" type="text" required maxLength={120} value={address.street}
                onChange={(event) => updateAddress('street', event.target.value)} className={inputClass} />
            </div>
            <div>
              <label htmlFor="number" className={labelClass}>Número</label>
              <input id="number" type="text" required maxLength={20} value={address.number}
                onChange={(event) => updateAddress('number', event.target.value)} className={inputClass} />
            </div>
            <div>
              <label htmlFor="complement" className={labelClass}>
                Complemento <span className="font-normal text-slate-400">(opcional)</span>
              </label>
              <input id="complement" type="text" maxLength={120} value={address.complement}
                onChange={(event) => updateAddress('complement', event.target.value)} className={inputClass} />
            </div>
            <div>
              <label htmlFor="district" className={labelClass}>Bairro</label>
              <input id="district" type="text" required maxLength={120} value={address.district}
                onChange={(event) => updateAddress('district', event.target.value)} className={inputClass} />
            </div>
            <div>
              <label htmlFor="city" className={labelClass}>Cidade</label>
              <input id="city" type="text" required maxLength={120} value={address.city}
                onChange={(event) => updateAddress('city', event.target.value)} className={inputClass} />
            </div>
            <div>
              <label htmlFor="state" className={labelClass}>Estado</label>
              <input id="state" type="text" required maxLength={2} placeholder="SP" value={address.state}
                onChange={(event) => updateAddress('state', event.target.value.toUpperCase())} className={inputClass} />
            </div>
            <div>
              <label htmlFor="zipCode" className={labelClass}>CEP</label>
              <input id="zipCode" type="text" required maxLength={20} placeholder="00000-000" value={address.zipCode}
                onChange={(event) => updateAddress('zipCode', event.target.value)} className={inputClass} />
            </div>
          </div>
        </section>

        <section className="rounded-xl bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold text-slate-800">Itens</h2>
            <button
              type="button"
              onClick={addItem}
              disabled={items.length >= 100}
              className="rounded-lg border border-orange-600 px-3 py-1 text-sm font-medium text-orange-600 transition hover:bg-orange-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              + Adicionar item
            </button>
          </div>
          <div className="space-y-3">
            {items.map((item, index) => (
              <div key={item.id} className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 p-3">
                <div className="min-w-40 flex-1">
                  <label htmlFor={`item-name-${index}`} className={labelClass}>Item</label>
                  <input id={`item-name-${index}`} type="text" required maxLength={120} value={item.name}
                    onChange={(event) => updateItem(index, { name: event.target.value })} className={inputClass} />
                </div>
                <div className="w-24">
                  <label htmlFor={`item-quantity-${index}`} className={labelClass}>Qtd.</label>
                  <input id={`item-quantity-${index}`} type="number" required min={1} max={1000} step={1} value={item.quantity}
                    onChange={(event) => updateItem(index, { quantity: event.target.value })} className={inputClass} />
                </div>
                <div className="w-32">
                  <label htmlFor={`item-price-${index}`} className={labelClass}>Preço unitário</label>
                  <input id={`item-price-${index}`} type="text" required inputMode="decimal" placeholder="0,00"
                    maxLength={9} value={item.unitPrice}
                    onChange={(event) => updateItem(index, { unitPrice: event.target.value })} className={inputClass} />
                </div>
                <button
                  type="button"
                  onClick={() => removeItem(index)}
                  disabled={items.length === 1}
                  className="rounded-lg px-3 py-2 text-sm font-medium text-slate-400 transition hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
                  title="Remover item"
                >
                  Remover
                </button>
              </div>
            ))}
          </div>
        </section>

        <div className="flex items-center justify-between rounded-xl bg-white p-5 shadow-sm">
          <span className="text-sm text-slate-600">
            Total estimado: <strong className="text-slate-800">{formatCurrency(total)}</strong>
          </span>
          <button
            type="submit"
            disabled={submitting}
            className="rounded-lg bg-orange-600 px-6 py-2 text-sm font-semibold text-white transition hover:bg-orange-700 disabled:opacity-60"
          >
            {submitting ? 'Criando…' : 'Criar pedido'}
          </button>
        </div>
      </form>
    </div>
  );
}
