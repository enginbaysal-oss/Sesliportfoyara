-- Web uygulamasının telefon numarasıyla yetki taklidi yapmasını engeller.
-- Yetki kaydı, oturum açmış Supabase kullanıcısının auth.uid() değeriyle eşleşmelidir.

alter table public.authorized_users enable row level security;

grant select on public.authorized_users to authenticated;

drop policy if exists authorized_users_select_self on public.authorized_users;
create policy authorized_users_select_self
on public.authorized_users
for select
to authenticated
using ((select auth.uid()) = auth_user_id);

create or replace function public.check_app_authorization_secure(p_phone text)
returns table (
    authorized boolean,
    full_name text,
    is_admin boolean,
    can_use_tools boolean
)
language sql
stable
security invoker
set search_path = ''
as $$
    select
        au.is_active as authorized,
        au.full_name,
        au.is_admin,
        au.can_use_tools
    from public.authorized_users au
    where au.auth_user_id = (select auth.uid())
      and right(regexp_replace(coalesce(au.phone, ''), '[^0-9]', '', 'g'), 10)
          = right(regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g'), 10)
      and au.is_active = true
    limit 1;
$$;

revoke all on function public.check_app_authorization_secure(text) from public;
revoke all on function public.check_app_authorization_secure(text) from anon;
grant execute on function public.check_app_authorization_secure(text) to authenticated;

