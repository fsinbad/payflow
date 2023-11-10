import { ListItemIcon, Menu, MenuItem, MenuProps } from '@mui/material';
import { ProfileType } from '../types/ProfleType';
import { Link, Payments, QrCode } from '@mui/icons-material';
import { comingSoonToast } from './Toasts';

export function FlowTopUpMenu(
  props: MenuProps & {
    profile: ProfileType;
  }
) {
  return (
    <Menu
      {...props}
      sx={{ mt: 1, '.MuiMenu-paper': { borderRadius: 5 } }}
      style={{ borderRadius: '50px' }}
      transformOrigin={{ horizontal: 'left', vertical: 'top' }}
      anchorOrigin={{ horizontal: 'left', vertical: 'bottom' }}>
      <MenuItem
        onClick={async () => {
          comingSoonToast();
        }}>
        <ListItemIcon>
          <QrCode fontSize="small" />
        </ListItemIcon>
        QR
      </MenuItem>
      <MenuItem
        onClick={() => {
          comingSoonToast();
        }}>
        <ListItemIcon>
          <Link fontSize="small" />
        </ListItemIcon>
        Link
      </MenuItem>
      <MenuItem
        onClick={() => {
          comingSoonToast();
        }}>
        <ListItemIcon>
          <Payments fontSize="small" />
        </ListItemIcon>
        Request
      </MenuItem>
    </Menu>
  );
}
