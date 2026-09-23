create schema if not exists private;

create table if not exists public.offices (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    code text not null unique check (code = upper(code) and char_length(code) between 4 and 24),
    created_by uuid references auth.users(id),
    max_users integer not null default 50 check (max_users between 1 and 500),
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.office_members (
    id uuid primary key default gen_random_uuid(),
    office_id uuid not null references public.offices(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    full_name text not null check (char_length(trim(full_name)) between 2 and 100),
    phone text not null,
    role text not null default 'advisor' check (role in ('admin', 'advisor', 'viewer')),
    status text not null default 'pending' check (status in ('pending', 'active', 'suspended')),
    permissions jsonb not null default '{"voice_search":true,"office_portfolios":true,"portfolio_write":true,"crm":true,"tapumatik":true,"document_assistant":true,"land_tracking":true}'::jsonb,
    approved_at timestamptz,
    approved_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (office_id, user_id),
    unique (office_id, phone)
);

create index if not exists office_members_user_id_idx on public.office_members(user_id);
create index if not exists office_members_office_id_idx on public.office_members(office_id);
create index if not exists office_members_office_status_idx on public.office_members(office_id, status);
create index if not exists office_members_approved_by_idx on public.office_members(approved_by);
create index if not exists offices_created_by_idx on public.offices(created_by);

create or replace function private.is_office_admin(target_office_id uuid, target_user_id uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.office_members m
        where m.office_id = target_office_id
          and m.user_id = target_user_id
          and m.role = 'admin'
          and m.status = 'active'
    );
$$;

revoke all on function private.is_office_admin(uuid, uuid) from public;
grant usage on schema private to authenticated;
grant execute on function private.is_office_admin(uuid, uuid) to authenticated;

create or replace function private.normalized_phone(value text)
returns text
language sql
immutable
set search_path = ''
as $$
    select right(regexp_replace(coalesce(value, ''), '[^0-9]', '', 'g'), 10);
$$;

create or replace function private.activate_pre_authorized_admin()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if exists (
        select 1
        from public.authorized_users au
        join auth.users u on u.id = new.user_id
        where au.is_active = true
          and au.is_admin = true
          and private.normalized_phone(au.phone) = private.normalized_phone(u.phone)
          and private.normalized_phone(new.phone) = private.normalized_phone(u.phone)
    ) then
        update public.office_members
        set role = 'admin', status = 'active', approved_at = now(), approved_by = new.user_id
        where id = new.id;
    end if;
    return null;
end;
$$;

drop trigger if exists activate_pre_authorized_admin_trigger on public.office_members;
create trigger activate_pre_authorized_admin_trigger
after insert on public.office_members
for each row execute function private.activate_pre_authorized_admin();

alter table public.offices enable row level security;
alter table public.office_members enable row level security;

drop policy if exists "authenticated users can find active offices" on public.offices;
create policy "authenticated users can find active offices"
on public.offices for select
to authenticated
using (is_active = true);

drop policy if exists "office admins can update their office" on public.offices;
create policy "office admins can update their office"
on public.offices for update
to authenticated
using ((select private.is_office_admin(id)))
with check ((select private.is_office_admin(id)));

drop policy if exists "members can read self or admins can read office" on public.office_members;
create policy "members can read self or admins can read office"
on public.office_members for select
to authenticated
using (
    user_id = (select auth.uid())
    or (select private.is_office_admin(office_id))
);

drop policy if exists "users can request office membership" on public.office_members;
create policy "users can request office membership"
on public.office_members for insert
to authenticated
with check (
    user_id = (select auth.uid())
    and role = 'advisor'
    and status = 'pending'
    and approved_at is null
    and approved_by is null
    and private.normalized_phone(phone) = private.normalized_phone((select auth.jwt() ->> 'phone'))
);

drop policy if exists "office admins can update memberships" on public.office_members;
create policy "office admins can update memberships"
on public.office_members for update
to authenticated
using ((select private.is_office_admin(office_id)))
with check ((select private.is_office_admin(office_id)));

grant select on public.offices to authenticated;
grant select, insert, update on public.office_members to authenticated;

insert into public.offices (name, code, created_by, max_users)
select 'RE/MAX İlyada 3', 'ILYADA3', au.auth_user_id, 50
from public.authorized_users au
where au.is_admin = true and au.auth_user_id is not null
limit 1
on conflict (code) do nothing;

insert into public.office_members (
    office_id, user_id, full_name, phone, role, status, approved_at, approved_by
)
select o.id, au.auth_user_id, au.full_name, au.phone, 'admin', 'active', now(), au.auth_user_id
from public.offices o
join public.authorized_users au on au.is_admin = true and au.auth_user_id is not null
where o.code = 'ILYADA3'
on conflict (office_id, user_id) do nothing;
